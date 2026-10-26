package edu.columbia.pulseguard.dto;

import java.util.List;

// AI-generated: Claude (Anthropic)
/**
 * Response of queryAnomalies.
 *
 * @param metricId the metric queried
 * @param total number of matching anomalies, ignoring the limit
 * @param anomalies matching anomalies, oldest first, at most limit entries
 */
public record AnomalyQueryResponse(Long metricId, long total, List<AnomalyView> anomalies) {
}
