package edu.columbia.pulseguard.detection;

import java.util.List;

// AI-generated: Claude (Anthropic)
/**
 * Immutable snapshot of what the detection engine needs to judge the next point.
 *
 * @param sampleCount number of points processed so far
 * @param ewma current EWMA level, or null before the first point
 * @param ewmaVariance current EWMA variance, or null before the first point
 * @param window most recent values (at most windowSize), oldest first
 */
public record DetectorState(long sampleCount, Double ewma, Double ewmaVariance,
    List<Double> window) {

  /**
   * Copies the window so the state cannot be modified after creation.
   */
  public DetectorState {
    window = List.copyOf(window);
  }

  /**
   * State of a metric that has not received any point yet.
   *
   * @return an empty state
   */
  public static DetectorState empty() {
    return new DetectorState(0, null, null, List.of());
  }
}
