package edu.columbia.pulseguard.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

// AI-generated: Claude (Anthropic)
/**
 * One data point sent by a client.
 *
 * @param timestamp ISO-8601 timestamp, e.g. 2026-10-26T14:00:00Z
 * @param value the numeric value
 */
public record DataPointInput(@NotNull Instant timestamp, @NotNull Double value) {
}
