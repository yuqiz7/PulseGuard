package edu.columbia.pulseguard.detection;

import edu.columbia.pulseguard.model.DetectionMethod;

/**
 * Detects anomalous values outside a configured inclusive range.
 *
 * <p>Values equal to either boundary are considered normal.
 * The first warmUpSamples values cannot be flagged as anomalies.
 */
public final class ThresholdDetector implements AnomalyDetector {
  @Override
  public DetectionResult evaluate(MetricRule rule, DetectorState state, double value) {
    validateRule(rule);
    validateValue(value);

    double lower = rule.params().get(RuleParams.LOWER_BOUND);
    double upper = rule.params().get(RuleParams.UPPER_BOUND);
    double score = 0.0;

    if (value < lower) {
      score = lower - value;
    } else if (value > upper) {
      score = value - upper;
    }

    boolean warmedUp = state.sampleCount() >= rule.warmUpSamples();
    boolean anomaly = warmedUp && score > 0.0;
    return new DetectionResult(anomaly, score, lower, upper);
  }

  @Override
  public DetectorState update(MetricRule rule, DetectorState state, double value) {
    validateRule(rule);
    validateValue(value);
    return new DetectorState(
        state.sampleCount() + 1, state.ewma(), state.ewmaVariance(), state.window());
  }

  private void validateRule(MetricRule rule) {
    if (rule.method() != DetectionMethod.THRESHOLD) {
      throw new IllegalArgumentException("Expected THRESHOLD detection method");
    }

    Double lower = rule.params().get(RuleParams.LOWER_BOUND);
    Double upper = rule.params().get(RuleParams.UPPER_BOUND);

    if (lower == null || upper == null) {
      throw new IllegalArgumentException("Both threshold bounds are required");
    }
    if (!Double.isFinite(lower) || !Double.isFinite(upper)) {
      throw new IllegalArgumentException("Threshold bounds must be finite");
    }
    if (lower > upper) {
      throw new IllegalArgumentException("Lower bound cannot exceed upper bound");
    }
    if (rule.warmUpSamples() < 0) {
      throw new IllegalArgumentException("Warm-up samples cannot be negative");
    }
  }

  private void validateValue(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("Metric value must be finite");
    }
  }
}
