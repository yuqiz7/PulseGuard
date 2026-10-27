package edu.columbia.pulseguard.api;

import edu.columbia.pulseguard.service.MetricRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * REST endpoint for registering metrics.
 */
@RestController
@RequestMapping("/metrics")
public class MetricController {

  private final MetricRegistrationService registrationService;

  /**
   * Creates the controller.
   *
   * @param registrationService implements registerMetric
   */
  public MetricController(MetricRegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  /**
   * registerMetric: {@code POST /metrics}.
   *
   * @param clientId the X-Client-Id header
   * @param request the metric name, detection method, parameters and warm-up count
   * @return the registered metric, including its ID
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MetricResponse registerMetric(
      @RequestHeader(ApiHeaders.CLIENT_ID) String clientId,
      @Valid @RequestBody RegisterMetricRequest request) {
    return MetricResponse.from(registrationService.register(clientId, request));
  }
}
