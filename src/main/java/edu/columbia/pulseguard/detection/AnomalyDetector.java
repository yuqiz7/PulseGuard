package edu.columbia.pulseguard.detection;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Contract of the detection engine (implemented in Area 2).
 *
 * <p>Ingestion calls {@link #evaluate} with the state before the point, then {@link #update}
 * to incorporate the point, so an outlier cannot hide itself by shifting the statistics.
 * Implementations must be pure: no database or Spring dependencies.
 */
public interface AnomalyDetector {

  /**
   * Judges one value against the current state without modifying it.
   *
   * <p>Must return a non-anomalous result while {@code state.sampleCount()} is below
   * {@code rule.warmUpSamples()}.
   *
   * @param rule the metric's detection rule
   * @param state the state before this value
   * @param value the value to judge
   * @return the verdict
   */
  DetectionResult evaluate(MetricRule rule, DetectorState state, double value);

  /**
   * Returns the state after incorporating one value.
   *
   * @param rule the metric's detection rule
   * @param state the state before this value
   * @param value the value to incorporate
   * @return the new state (sample count incremented, window trimmed to windowSize)
   */
  DetectorState update(MetricRule rule, DetectorState state, double value);
}
