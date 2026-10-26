package edu.columbia.pulseguard.dto;

import java.util.List;

// AI-generated: Claude (Anthropic)
/**
 * Response of submitDataPoints.
 *
 * @param metricId the metric the points were stored for
 * @param accepted number of points stored
 * @param anomalies number of points flagged as anomalies
 * @param results verdict for each point, oldest first
 */
public record SubmitDataPointsResponse(Long metricId, int accepted, int anomalies,
    List<PointResult> results) {
}
