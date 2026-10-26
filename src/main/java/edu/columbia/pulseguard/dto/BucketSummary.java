package edu.columbia.pulseguard.dto;

import java.time.Instant;

// AI-generated: Claude (Anthropic)
/**
 * Summary statistics of one time bucket.
 *
 * @param start start of the bucket (UTC, truncated to the hour or day)
 * @param stats statistics of the points in the bucket
 */
public record BucketSummary(Instant start, SummaryStats stats) {
}
