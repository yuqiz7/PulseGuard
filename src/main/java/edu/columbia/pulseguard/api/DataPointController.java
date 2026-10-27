package edu.columbia.pulseguard.api;

import edu.columbia.pulseguard.service.DataIngestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * REST endpoint for submitting data points.
 */
@RestController
@RequestMapping("/metrics/{metricId}/datapoints")
public class DataPointController {

  private final DataIngestionService ingestionService;

  /**
   * Creates the controller.
   *
   * @param ingestionService implements submitDataPoints
   */
  public DataPointController(DataIngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  /**
   * submitDataPoints: {@code POST /metrics/{metricId}/datapoints}.
   *
   * @param clientId the X-Client-Id header
   * @param metricId the metric ID
   * @param request a batch of 1 to 1000 points in increasing timestamp order
   * @return the number of stored points and anomalies, and a verdict per point
   */
  @PostMapping
  public SubmitDataPointsResponse submitDataPoints(
      @RequestHeader(ApiHeaders.CLIENT_ID) String clientId,
      @PathVariable Long metricId,
      @Valid @RequestBody SubmitDataPointsRequest request) {
    return ingestionService.submit(clientId, metricId, request);
  }
}
