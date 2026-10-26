package edu.columbia.pulseguard.dto;

import edu.columbia.pulseguard.model.Anomaly;
import edu.columbia.pulseguard.model.DetectionMethod;
import java.time.Instant;

// AI-generated: Claude (Anthropic)
/**
 * A stored anomaly as returned by queryAnomalies.
 *
 * @param timestamp timestamp of the anomalous point
 * @param value the anomalous value
 * @param score the anomaly score
 * @param expectedLow lower end of the expected range
 * @param expectedHigh upper end of the expected range
 * @param method detection method in effect when it was detected
 */
public record AnomalyView(Instant timestamp, double value, double score, double expectedLow,
    double expectedHigh, DetectionMethod method) {

  /**
   * Builds the view of a stored anomaly.
   *
   * @param anomaly the stored anomaly
   * @return the view
   */
  public static AnomalyView from(Anomaly anomaly) {
    return new AnomalyView(anomaly.getTs(), anomaly.getValue(), anomaly.getScore(),
        anomaly.getExpectedLow(), anomaly.getExpectedHigh(), anomaly.getMethod());
  }
}
