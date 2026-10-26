package edu.columbia.pulseguard.detection;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// AI-generated: Claude (Anthropic)
/**
 * Detection engine implementing rolling z-score, EWMA and static-threshold detection.
 *
 * <p>The engine is pure: it only computes results from its inputs and never touches the
 * database. The rolling window and the EWMA state are always maintained, whatever the method,
 * so that a metric can switch methods later without losing history.
 */
@Component
public class DetectionEngine implements AnomalyDetector {

  /** Floor for the standard deviation, so a constant series does not divide by zero. */
  static final double MIN_STDDEV = 1e-9;

  /**
   * Judges one value against the state before it.
   *
   * @param rule the metric's detection rule
   * @param state the state before this value
   * @param value the value to judge
   * @return the verdict; never anomalous during warm-up
   */
  @Override
  public DetectionResult evaluate(MetricRule rule, DetectorState state, double value) {
    if (state.sampleCount() < rule.warmUpSamples()) {
      return notAnomalous(value);
    }
    switch (rule.method()) {
      case ZSCORE:
        return evaluateZscore(rule, state, value);
      case EWMA:
        return evaluateEwma(rule, state, value);
      case THRESHOLD:
        return evaluateThreshold(rule, value);
      default:
        throw new IllegalArgumentException("Unsupported detection method: " + rule.method());
    }
  }

  /**
   * Incorporates one value: increments the count, updates the EWMA level and variance, and
   * appends the value to the window (keeping at most windowSize values).
   *
   * @param rule the metric's detection rule
   * @param state the state before this value
   * @param value the value to incorporate
   * @return the new state
   */
  @Override
  public DetectorState update(MetricRule rule, DetectorState state, double value) {
    Double ewma;
    Double ewmaVariance;
    if (state.ewma() == null) {
      ewma = value;
      ewmaVariance = 0.0;
    } else {
      double alpha = rule.param(RuleParams.ALPHA, RuleParams.DEFAULT_ALPHA);
      double previousVariance = state.ewmaVariance() == null ? 0.0 : state.ewmaVariance();
      double diff = value - state.ewma();
      double increment = alpha * diff;
      ewma = state.ewma() + increment;
      ewmaVariance = (1 - alpha) * (previousVariance + diff * increment);
    }

    int windowSize = windowSize(rule);
    List<Double> window = new ArrayList<>(state.window());
    window.add(value);
    while (window.size() > windowSize) {
      window.remove(0);
    }
    return new DetectorState(state.sampleCount() + 1, ewma, ewmaVariance, window);
  }

  private DetectionResult evaluateZscore(MetricRule rule, DetectorState state, double value) {
    List<Double> window = state.window();
    if (window.size() < 2) {
      return notAnomalous(value);
    }
    double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(value);
    double variance = window.stream()
        .mapToDouble(v -> (v - mean) * (v - mean))
        .sum() / window.size();
    double threshold = rule.param(RuleParams.Z_THRESHOLD, RuleParams.DEFAULT_Z_THRESHOLD);
    return deviationResult(value, mean, Math.sqrt(variance), threshold);
  }

  private DetectionResult evaluateEwma(MetricRule rule, DetectorState state, double value) {
    if (state.ewma() == null) {
      return notAnomalous(value);
    }
    double variance = state.ewmaVariance() == null ? 0.0 : state.ewmaVariance();
    double tolerance = rule.param(RuleParams.TOLERANCE, RuleParams.DEFAULT_TOLERANCE);
    return deviationResult(value, state.ewma(), Math.sqrt(variance), tolerance);
  }

  private DetectionResult evaluateThreshold(MetricRule rule, double value) {
    double lower = rule.param(RuleParams.LOWER_BOUND, -Double.MAX_VALUE);
    double upper = rule.param(RuleParams.UPPER_BOUND, Double.MAX_VALUE);
    double distance = 0.0;
    if (value < lower) {
      distance = lower - value;
    } else if (value > upper) {
      distance = value - upper;
    }
    return new DetectionResult(distance > 0, distance, lower, upper);
  }

  /**
   * Flags a value whose distance from the expected level exceeds limit standard deviations.
   */
  private DetectionResult deviationResult(double value, double expected, double stddev,
      double limit) {
    double effectiveStddev = Math.max(stddev, MIN_STDDEV);
    double score = Math.abs(value - expected) / effectiveStddev;
    double halfWidth = limit * stddev;
    return new DetectionResult(score > limit, score, expected - halfWidth, expected + halfWidth);
  }

  private static DetectionResult notAnomalous(double value) {
    return new DetectionResult(false, 0.0, value, value);
  }

  /**
   * Window size configured on the rule, or the default.
   *
   * @param rule the detection rule
   * @return the number of values kept in the rolling window
   */
  public static int windowSize(MetricRule rule) {
    return (int) rule.param(RuleParams.WINDOW_SIZE, RuleParams.DEFAULT_WINDOW_SIZE);
  }
}
