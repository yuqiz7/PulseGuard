package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.api.ConflictException;
import edu.columbia.pulseguard.detection.RuleValidator;
import edu.columbia.pulseguard.dto.RegisterMetricRequest;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.model.MetricStatistics;
import edu.columbia.pulseguard.repository.MetricRepository;
import edu.columbia.pulseguard.repository.MetricStatisticsRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI-generated: Claude (Anthropic)
/**
 * Implements registerMetric: validates the rule, stores the metric and its empty statistics.
 */
@Service
public class MetricRegistrationService {

  private final MetricLookupService lookupService;
  private final MetricRepository metricRepository;
  private final MetricStatisticsRepository statisticsRepository;

  /**
   * Creates the service.
   *
   * @param lookupService shared client ID checks
   * @param metricRepository metric data access
   * @param statisticsRepository statistics data access
   */
  public MetricRegistrationService(MetricLookupService lookupService,
      MetricRepository metricRepository, MetricStatisticsRepository statisticsRepository) {
    this.lookupService = lookupService;
    this.metricRepository = metricRepository;
    this.statisticsRepository = statisticsRepository;
  }

  /**
   * Registers a new metric for the calling client.
   *
   * @param clientId the X-Client-Id header value
   * @param request the validated request body
   * @return the stored metric
   * @throws edu.columbia.pulseguard.api.UnauthorizedException if the client ID is blank
   * @throws edu.columbia.pulseguard.api.BadRequestException if the rule is invalid
   * @throws ConflictException if the client already has a metric with this name
   */
  @Transactional
  public Metric register(String clientId, RegisterMetricRequest request) {
    String owner = lookupService.requireClientId(clientId);
    String name = request.name().trim();
    Map<String, Double> params = RuleValidator.normalizeParams(request.method(), request.params());
    int warmUpSamples = RuleValidator.normalizeWarmUp(request.method(), request.warmUpSamples());
    if (metricRepository.existsByClientIdAndName(owner, name)) {
      throw new ConflictException("Metric '" + name + "' already exists for this client");
    }
    Metric metric = metricRepository.save(
        new Metric(owner, name, request.unit(), request.method(), params, warmUpSamples));
    statisticsRepository.save(new MetricStatistics(metric.getId()));
    return metric;
  }
}
