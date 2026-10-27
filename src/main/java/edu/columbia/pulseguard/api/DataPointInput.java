package edu.columbia.pulseguard.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * One data point of a submitDataPoints request.
 *
 * @param timestamp when the value was observed (ISO-8601, e.g. 2026-10-20T12:00:00Z)
 * @param value the observed value; must be a finite number
 */
public record DataPointInput(@NotNull Instant timestamp, @NotNull Double value) {
}
