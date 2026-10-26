package edu.columbia.pulseguard.dto;

import edu.columbia.pulseguard.model.DetectionMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

// AI-generated: Claude (Anthropic)
/**
 * Request body of registerMetric (POST /metrics).
 *
 * @param name metric name, unique for the calling client
 * @param unit optional unit label
 * @param method detection method: ZSCORE, EWMA or THRESHOLD
 * @param params optional detection parameters; defaults are applied for missing keys
 * @param warmUpSamples optional warm-up count (default 10, or 0 for THRESHOLD)
 */
public record RegisterMetricRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 50) String unit,
    @NotNull DetectionMethod method,
    Map<String, Double> params,
    Integer warmUpSamples) {
}
