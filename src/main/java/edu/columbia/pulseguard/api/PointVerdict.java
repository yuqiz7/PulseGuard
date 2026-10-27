package edu.columbia.pulseguard.api;

import java.time.Instant;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * The verdict computed for one submitted point.
 *
 * @param timestamp the point's timestamp
 * @param value the submitted value
 * @param anomaly whether the point was flagged
 * @param score the anomaly score
 * @param expectedLow lower end of the expected range
 * @param expectedHigh upper end of the expected range
 */
public record PointVerdict(Instant timestamp, double value, boolean anomaly, double score,
    double expectedLow, double expectedHigh) {
}
