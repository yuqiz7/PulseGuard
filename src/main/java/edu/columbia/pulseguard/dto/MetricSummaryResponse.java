package edu.columbia.pulseguard.dto;

import java.time.Instant;
import java.util.List;

// AI-generated: Claude (Anthropic)
/**
 * Response of getMetricSummary.
 *
 * @param metricId the metric summarized
 * @param from inclusive start of the range
 * @param to inclusive end of the range
 * @param overall statistics of the whole range
 * @param bucket the bucket size used ("hour" or "day"), or null if none was requested
 * @param buckets per-bucket statistics, oldest first; empty if no bucket was requested
 */
public record MetricSummaryResponse(Long metricId, Instant from, Instant to,
    SummaryStats overall, String bucket, List<BucketSummary> buckets) {
}
