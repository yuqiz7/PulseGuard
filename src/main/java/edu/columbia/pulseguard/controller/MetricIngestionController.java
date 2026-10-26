package edu.columbia.pulseguard.controller;

import edu.columbia.pulseguard.api.ApiHeaders;
import edu.columbia.pulseguard.dto.MetricResponse;
import edu.columbia.pulseguard.dto.RegisterMetricRequest;
import edu.columbia.pulseguard.dto.SubmitDataPointsRequest;
import edu.columbia.pulseguard.dto.SubmitDataPointsResponse;
import edu.columbia.pulseguard.service.DataIngestionService;
import edu.columbia.pulseguard.service.MetricRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// AI-generated: Claude (Anthropic)
/**
 * Endpoints for registering metrics and submitting data points.
 */
@RestController
@RequestMapping("/metrics")
public class MetricIngestionController {

  private final MetricRegistrationService registrationService;
  private final DataIngestionService ingestionService;

  /**
   * Creates the controller.
   *
   * @param registrationService registerMetric implementation
   * @param ingestionService submitDataPoints implementation
   */
  public MetricIngestionController(MetricRegistrationService registrationService,
      DataIngestionService ingestionService) {
    this.registrationService = registrationService;
    this.ingestionService = ingestionService;
  }

  /**
   * registerMetric: POST /metrics.
   *
   * @param clientId the X-Client-Id header
   * @param request the metric definition
   * @return 201 with the stored metric
   */
  @PostMapping
  public ResponseEntity<MetricResponse> registerMetric(
      @RequestHeader(ApiHeaders.CLIENT_ID) String clientId,
      @Valid @RequestBody RegisterMetricRequest request) {
    MetricResponse body = MetricResponse.from(registrationService.register(clientId, request));
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * submitDataPoints: POST /metrics/{metricId}/datapoints.
   *
   * @param clientId the X-Client-Id header
   * @param metricId the metric ID
   * @param request the batch of points
   * @return 200 with the verdict for every point
   */
  @PostMapping("/{metricId}/datapoints")
  public SubmitDataPointsResponse submitDataPoints(
      @RequestHeader(ApiHeaders.CLIENT_ID) String clientId,
      @PathVariable("metricId") Long metricId,
      @Valid @RequestBody SubmitDataPointsRequest request) {
    return ingestionService.submit(clientId, metricId, request.points());
  }
}
