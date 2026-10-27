package edu.columbia.pulseguard.api;

import java.util.List;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Response body of submitDataPoints.
 *
 * @param accepted number of points stored
 * @param anomalies number of those points flagged as anomalies
 * @param results one verdict per submitted point, in submission order
 */
public record SubmitDataPointsResponse(int accepted, int anomalies, List<PointVerdict> results) {
}
