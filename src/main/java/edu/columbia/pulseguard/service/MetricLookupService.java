package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.api.ApiHeaders;
import edu.columbia.pulseguard.api.NotFoundException;
import edu.columbia.pulseguard.api.UnauthorizedException;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.repository.MetricRepository;
import org.springframework.stereotype.Service;

// AI-generated: Claude (Anthropic)
/**
 * Shared access checks: validates the client ID and loads metrics owned by that client.
 *
 * <p>Every endpoint that takes a metricId must load the metric through
 * {@link #requireOwnedMetric}, so another client's metric is reported as 404.
 */
@Service
public class MetricLookupService {

  private final MetricRepository metricRepository;

  /**
   * Creates the service.
   *
   * @param metricRepository metric data access
   */
  public MetricLookupService(MetricRepository metricRepository) {
    this.metricRepository = metricRepository;
  }

  /**
   * Validates the X-Client-Id value.
   *
   * @param clientId the header value
   * @return the trimmed client ID
   * @throws UnauthorizedException if the value is null or blank
   */
  public String requireClientId(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      throw new UnauthorizedException("Missing " + ApiHeaders.CLIENT_ID + " header");
    }
    return clientId.trim();
  }

  /**
   * Loads a metric owned by the calling client.
   *
   * @param clientId the X-Client-Id header value
   * @param metricId the metric ID from the URL
   * @return the metric
   * @throws UnauthorizedException if the client ID is missing or blank
   * @throws NotFoundException if the metric does not exist or belongs to another client
   */
  public Metric requireOwnedMetric(String clientId, Long metricId) {
    String owner = requireClientId(clientId);
    return metricRepository.findByIdAndClientId(metricId, owner)
        .orElseThrow(() -> new NotFoundException("Metric " + metricId + " not found"));
  }
}
