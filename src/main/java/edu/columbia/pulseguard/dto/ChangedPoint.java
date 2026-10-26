package edu.columbia.pulseguard.dto;

import java.time.Instant;

// AI-generated: Claude (Anthropic)
/**
 * A replayed point whose anomaly flag differs under the new rule.
 *
 * @param timestamp the point's timestamp
 * @param value the point's value
 * @param previousAnomaly flag under the current rule
 * @param newAnomaly flag under the new rule
 * @param newScore anomaly score under the new rule
 */
public record ChangedPoint(Instant timestamp, double value, boolean previousAnomaly,
    boolean newAnomaly, double newScore) {
}
