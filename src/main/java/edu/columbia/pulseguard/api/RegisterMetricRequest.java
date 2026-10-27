package edu.columbia.pulseguard.api;

import edu.columbia.pulseguard.model.DetectionMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Request body of registerMetric.
 *
 * @param name the metric name, unique per client
 * @param unit an optional unit label such as "ms"
 * @param method the detection method
 * @param params detection parameters; omitted keys get the method's defaults
 * @param warmUpSamples number of initial points that are never flagged; omitted means the
 *     default for the method
 */
public record RegisterMetricRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 50) String unit,
    @NotNull DetectionMethod method,
    Map<String, Double> params,
    Integer warmUpSamples) {
}
