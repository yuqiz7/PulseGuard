package edu.columbia.pulseguard.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Request body of submitDataPoints: a batch of 1 to 1000 points in increasing timestamp order.
 *
 * @param dataPoints the points to ingest
 */
public record SubmitDataPointsRequest(
    @NotEmpty @Size(max = SubmitDataPointsRequest.MAX_BATCH_SIZE)
    List<@NotNull @Valid DataPointInput> dataPoints) {

  /** Largest accepted batch. */
  public static final int MAX_BATCH_SIZE = 1000;
}
