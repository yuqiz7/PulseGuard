package edu.columbia.pulseguard.detection;

// AI-generated: Claude (Anthropic)
/**
 * Verdict for one value.
 *
 * @param anomaly whether the value is flagged as an anomaly
 * @param score anomaly score (for ZSCORE/EWMA the number of standard deviations from the
 *     expected level; for THRESHOLD the distance outside the bounds, 0 inside them)
 * @param expectedLow lower end of the expected range
 * @param expectedHigh upper end of the expected range
 */
public record DetectionResult(boolean anomaly, double score, double expectedLow,
    double expectedHigh) {
}
