package edu.columbia.pulseguard.dto;

import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.List;
import java.util.Map;

// AI-generated: Claude (Anthropic)
/**
 * Response of updateDetectionRule.
 *
 * @param metricId the metric
 * @param applied true if the new rule was saved (dryRun=false), false for a dry run
 * @param method the new detection method
 * @param params the new parameters, including defaults
 * @param warmUpSamples the new warm-up count
 * @param replayedPoints number of most recent points replayed under the new rule
 * @param totalPoints number of points stored for the metric
 * @param truncated true if older points were not replayed because of the replay limit
 * @param previousAnomalyCount anomalies among the replayed points under the current rule
 * @param newAnomalyCount anomalies among the replayed points under the new rule
 * @param changedPointCount number of replayed points whose flag changed
 * @param changedPoints the first changed points (at most 100), oldest first
 */
public record RuleUpdateResponse(Long metricId, boolean applied, DetectionMethod method,
    Map<String, Double> params, int warmUpSamples, int replayedPoints, long totalPoints,
    boolean truncated, int previousAnomalyCount, int newAnomalyCount, int changedPointCount,
    List<ChangedPoint> changedPoints) {
}
