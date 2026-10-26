package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.dto.AnomalyQueryResponse;
import edu.columbia.pulseguard.dto.AnomalyView;
import edu.columbia.pulseguard.repository.AnomalyRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI-generated: Claude (Anthropic)
/**
 * Implements queryAnomalies: lists a metric's stored anomalies in a time range.
 */
@Service
public class AnomalyQueryService {

  /** Largest accepted limit. */
  public static final int MAX_LIMIT = 1000;

  private final MetricLookupService lookupService;
  private final AnomalyRepository anomalyRepository;

  /**
   * Creates the service.
   *
   * @param lookupService shared client ID and ownership checks
   * @param anomalyRepository anomaly access
   */
  public AnomalyQueryService(MetricLookupService lookupService,
      AnomalyRepository anomalyRepository) {
    this.lookupService = lookupService;
    this.anomalyRepository = anomalyRepository;
  }

  /**
   * Returns the anomalies of a metric owned by the caller.
   *
   * @param clientId the X-Client-Id header value
   * @param metricId the metric ID
   * @param from inclusive start of the range
   * @param to inclusive end of the range
   * @param minScore minimum anomaly score, at least 0
   * @param limit maximum number of anomalies returned, 1 to 1000
   * @return matching anomalies, oldest first, and the total count
   * @throws BadRequestException if the range, score or limit is invalid
   */
  @Transactional(readOnly = true)
  public AnomalyQueryResponse query(String clientId, Long metricId, Instant from, Instant to,
      double minScore, int limit) {
    lookupService.requireOwnedMetric(clientId, metricId);
    RangeValidator.requireValidRange(from, to);
    if (!Double.isFinite(minScore) || minScore < 0) {
      throw new BadRequestException("minScore must be a number greater than or equal to 0");
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
    }
    List<AnomalyView> anomalies = anomalyRepository
        .findByMetricIdAndTsBetweenAndScoreGreaterThanEqualOrderByTsAsc(
            metricId, from, to, minScore, PageRequest.of(0, limit))
        .stream()
        .map(AnomalyView::from)
        .toList();
    long total = anomalyRepository.countByMetricIdAndTsBetweenAndScoreGreaterThanEqual(
        metricId, from, to, minScore);
    return new AnomalyQueryResponse(metricId, total, anomalies);
  }
}
