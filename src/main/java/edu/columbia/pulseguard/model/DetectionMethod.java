package edu.columbia.pulseguard.model;

// AI-generated: Claude (Anthropic)
/**
 * Detection methods a metric can be configured with.
 */
public enum DetectionMethod {
  /** Rolling-window z-score: flags values far from the mean of the last windowSize points. */
  ZSCORE,
  /** Exponentially weighted moving average: flags values far from the smoothed level. */
  EWMA,
  /** Static bounds: flags values outside [lowerBound, upperBound]. */
  THRESHOLD
}
