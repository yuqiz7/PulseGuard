package edu.columbia.pulseguard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// AI-generated: Claude (Anthropic)
/**
 * Request body of submitDataPoints (POST /metrics/{metricId}/datapoints).
 *
 * @param points 1 to 1000 data points; they may be in any order but timestamps must be unique
 *     and later than the latest stored point
 */
public record SubmitDataPointsRequest(
    @NotNull @Size(min = 1, max = 1000) List<@Valid @NotNull DataPointInput> points) {
}
