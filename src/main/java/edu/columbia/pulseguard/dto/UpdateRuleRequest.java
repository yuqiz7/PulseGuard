package edu.columbia.pulseguard.dto;

import edu.columbia.pulseguard.model.DetectionMethod;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

// AI-generated: Claude (Anthropic)
/**
 * Request body of updateDetectionRule (PUT /metrics/{metricId}/rule).
 *
 * @param method the new detection method
 * @param params optional new parameters; defaults are applied for missing keys
 * @param warmUpSamples optional new warm-up count (default 10, or 0 for THRESHOLD)
 */
public record UpdateRuleRequest(@NotNull DetectionMethod method, Map<String, Double> params,
    Integer warmUpSamples) {
}
