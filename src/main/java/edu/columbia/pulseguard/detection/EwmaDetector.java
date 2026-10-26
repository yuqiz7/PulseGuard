package edu.columbia.pulseguard.detection;

import edu.columbia.pulseguard.model.DetectionMethod;

/**
 * Detects anomalies using an exponentially weighted moving
 * average (EWMA) and exponentially weighted variance.
 *
 * <p>Each value is evaluated against the previous statistical
 * state before that state is updated.
 */
public final class EwmaDetector implements AnomalyDetector {

  @Override
  public DetectionResult evaluate(MetricRule rule, DetectorState state, double value) {
    validateRule(rule);
    validateState(state);
    validateValue(value);

    if (state.sampleCount() == 0) {
      return new DetectionResult(false, 0.0, value, value);
    }

    double tolerance = rule.param(RuleParams.TOLERANCE, RuleParams.DEFAULT_TOLERANCE);
    double mean = state.ewma();
    double variance = state.ewmaVariance();
    double stddev = Math.sqrt(variance);
    double margin = tolerance * stddev;
    double expectedLow = mean - margin;
    double expectedHigh = mean + margin;

    if (!Double.isFinite(expectedLow) || !Double.isFinite(expectedHigh)) {
      throw new IllegalArgumentException("EWMA expected range overflow");
    }

    double difference = Math.abs(value - mean);
    double score;
    boolean exceedsTolerance;

    if (stddev == 0.0) {
      exceedsTolerance = difference > 0.0;
      score = exceedsTolerance ? Double.MAX_VALUE : 0.0;
    } else {
      double rawScore = difference / stddev;
      score = Double.isFinite(rawScore) ? rawScore : Double.MAX_VALUE;
      exceedsTolerance = rawScore > tolerance;
    }

    boolean warmedUp = state.sampleCount() >= rule.warmUpSamples();
    boolean anomaly = warmedUp && exceedsTolerance;
    return new DetectionResult(anomaly, score, expectedLow, expectedHigh);
  }

  @Override
  public DetectorState update(MetricRule rule, DetectorState state, double value) {
    validateRule(rule);
    validateState(state);
    validateValue(value);

    if (state.sampleCount() == 0) {
      return new DetectorState(1, value, 0.0, state.window());
    }

    double alpha = rule.param(RuleParams.ALPHA, RuleParams.DEFAULT_ALPHA);
    double oldMean = state.ewma();
    double oldVariance = state.ewmaVariance();
    double difference = value - oldMean;
    double newMean = oldMean + alpha * difference;
    double newVariance = (1.0 - alpha) * (oldVariance + alpha * difference * difference);

    if (!Double.isFinite(newMean) || !Double.isFinite(newVariance)) {
      throw new IllegalArgumentException("EWMA statistics overflow");
    }

    return new DetectorState(state.sampleCount() + 1, newMean, newVariance, state.window());
  }

  private void validateRule(MetricRule rule) {
    if (rule.method() != DetectionMethod.EWMA) {
      throw new IllegalArgumentException("Expected EWMA detection method");
    }

    double alpha = rule.param(RuleParams.ALPHA, RuleParams.DEFAULT_ALPHA);
    double tolerance = rule.param(RuleParams.TOLERANCE, RuleParams.DEFAULT_TOLERANCE);
    if (!Double.isFinite(alpha) || alpha <= 0.0 || alpha >= 1.0) {
      throw new IllegalArgumentException("EWMA alpha must be strictly between 0 and 1");
    }
    if (!Double.isFinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("EWMA tolerance must be positive and finite");
    }
    if (rule.warmUpSamples() < 0) {
      throw new IllegalArgumentException("Warm-up samples cannot be negative");
    }
  }

  private void validateState(DetectorState state) {
    if (state.sampleCount() < 0) {
      throw new IllegalArgumentException("Sample count cannot be negative");
    }
    if (state.sampleCount() == 0) {
      return;
    }
    if (state.ewma() == null || state.ewmaVariance() == null) {
      throw new IllegalArgumentException("EWMA state is incomplete");
    }
    if (!Double.isFinite(state.ewma())
        || !Double.isFinite(state.ewmaVariance())
        || state.ewmaVariance() < 0.0) {
      throw new IllegalArgumentException("EWMA state is invalid");
    }
  }

  private void validateValue(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("Metric value must be finite");
    }
  }
}
