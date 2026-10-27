package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.api.ConflictException;
import edu.columbia.pulseguard.api.RegisterMetricRequest;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.model.MetricStatistics;
import edu.columbia.pulseguard.repository.MetricRepository;
import edu.columbia.pulseguard.repository.MetricStatisticsRepository;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Implements registerMetric: validates the rule, rejects duplicate names and stores the metric
 * together with its empty statistics row.
 */
@Service
public class MetricRegistrationService {

  private final MetricRepository metricRepository;
  private final MetricStatisticsRepository statisticsRepository;
  private final MetricLookupService lookupService;

  /**
   * Creates the service.
   *
   * @param metricRepository metric data access
   * @param statisticsRepository statistics data access
   * @param lookupService shared client ID validation
   */
  public MetricRegistrationService(MetricRepository metricRepository,
      MetricStatisticsRepository statisticsRepository, MetricLookupService lookupService) {
    this.metricRepository = metricRepository;
    this.statisticsRepository = statisticsRepository;
    this.lookupService = lookupService;
  }

  /**
   * Registers a new metric for a client.
   *
   * @param clientId the X-Client-Id header value
   * @param request the validated request body
   * @return the stored metric
   * @throws edu.columbia.pulseguard.api.UnauthorizedException if the client ID is missing
   * @throws edu.columbia.pulseguard.api.BadRequestException if the rule is invalid
   * @throws ConflictException if the client already has a metric with this name
   */
  @Transactional
  public Metric register(String clientId, RegisterMetricRequest request) {
    String owner = lookupService.requireClientId(clientId);
    String name = request.name().trim();
    Map<String, Double> params =
        MetricRuleValidator.normalizeParams(request.method(), request.params());
    int warmUp = MetricRuleValidator.normalizeWarmUp(request.method(), request.warmUpSamples());

    if (metricRepository.existsByClientIdAndName(owner, name)) {
      throw duplicate(name);
    }
    Metric metric = new Metric(owner, name, request.unit(), request.method(), params, warmUp);
    try {
      metric = metricRepository.saveAndFlush(metric);
    } catch (DataIntegrityViolationException e) {
      // Another request registered the same name between the check above and the insert.
      throw duplicate(name);
    }
    statisticsRepository.save(new MetricStatistics(metric.getId()));
    return metric;
  }

  private static ConflictException duplicate(String name) {
    return new ConflictException("Metric '" + name + "' already exists for this client");
  }
}
