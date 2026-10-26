package edu.columbia.pulseguard.dto;

// AI-generated: Claude (Anthropic)
/**
 * Summary statistics of a set of points; all fields except counts are null when it is empty.
 *
 * @param count number of points
 * @param anomalies number of points flagged as anomalies
 * @param mean mean value
 * @param stddev population standard deviation
 * @param min smallest value
 * @param max largest value
 * @param anomalyRate anomalies divided by count
 * @param trendPerHour slope of a least-squares line through the points, in value units per
 *     hour; null with fewer than two distinct timestamps
 */
public record SummaryStats(long count, long anomalies, Double mean, Double stddev, Double min,
    Double max, Double anomalyRate, Double trendPerHour) {
}
