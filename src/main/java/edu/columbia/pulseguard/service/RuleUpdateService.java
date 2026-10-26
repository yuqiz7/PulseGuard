package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.detection.AnomalyDetector;
import edu.columbia.pulseguard.detection.DetectionResult;
import edu.columbia.pulseguard.detection.DetectorState;
import edu.columbia.pulseguard.detection.MetricRule;
import edu.columbia.pulseguard.detection.RuleValidator;
import edu.columbia.pulseguard.dto.ChangedPoint;
import edu.columbia.pulseguard.dto.RuleUpdateResponse;
import edu.columbia.pulseguard.dto.UpdateRuleRequest;
import edu.columbia.pulseguard.model.Anomaly;
import edu.columbia.pulseguard.model.DataPoint;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.model.MetricStatistics;
import edu.columbia.pulseguard.repository.AnomalyRepository;
import edu.columbia.pulseguard.repository.DataPointRepository;
import edu.columbia.pulseguard.repository.MetricRepository;
import edu.columbia.pulseguard.repository.MetricStatisticsRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI-generated: Claude (Anthropic)
/**
 * Implements updateDetectionRule: replays the most recent history under a new rule, reports
 * how the verdicts change, and optionally saves the rule and the recomputed verdicts.
 *
 * <p>To keep the request fast for metrics with long histories, at most {@link #REPLAY_LIMIT}
 * of the most recent points are replayed; older points keep their existing verdicts.
 */
@Service
public class RuleUpdateService {

  /** Maximum number of most recent points replayed under a new rule. */
  public static final int REPLAY_LIMIT = 10_000;
  /** Maximum number of changed points listed in the response. */
  public static final int MAX_CHANGED_POINTS = 100;

  private final MetricLookupService lookupService;
  private final MetricRepository metricRepository;
  private final DataPointRepository dataPointRepository;
  private final AnomalyRepository anomalyRepository;
  private final MetricStatisticsRepository statisticsRepository;
  private final AnomalyDetector detector;

  /**
   * Creates the service.
   *
   * @param lookupService shared client ID and ownership checks
   * @param metricRepository metric access
   * @param dataPointRepository data point access
   * @param anomalyRepository anomaly access
   * @param statisticsRepository statistics access (with row locking)
   * @param detector the detection engine
   */
  public RuleUpdateService(MetricLookupService lookupService, MetricRepository metricRepository,
      DataPointRepository dataPointRepository, AnomalyRepository anomalyRepository,
      MetricStatisticsRepository statisticsRepository, AnomalyDetector detector) {
    this.lookupService = lookupService;
    this.metricRepository = metricRepository;
    this.dataPointRepository = dataPointRepository;
    this.anomalyRepository = anomalyRepository;
    this.statisticsRepository = statisticsRepository;
    this.detector = detector;
  }

  /**
   * Evaluates a new rule against recent history and, unless this is a dry run, applies it.
   *
   * @param clientId the X-Client-Id header value
   * @param metricId the metric ID
   * @param request the new rule
   * @param dryRun if true, nothing is saved
   * @return anomaly counts under the old and new rules and the points whose flag changed
   * @throws edu.columbia.pulseguard.api.NotFoundException if the metric is not the caller's
   * @throws edu.columbia.pulseguard.api.BadRequestException if the new rule is invalid
   */
  @Transactional
  public RuleUpdateResponse update(String clientId, Long metricId, UpdateRuleRequest request,
      boolean dryRun) {
    Metric metric = lookupService.requireOwnedMetric(clientId, metricId);
    Map<String, Double> params = RuleValidator.normalizeParams(request.method(), request.params());
    int warmUpSamples = RuleValidator.normalizeWarmUp(request.method(), request.warmUpSamples());
    MetricRule newRule = new MetricRule(request.method(), params, warmUpSamples);
    MetricStatistics stats = statisticsRepository.findByIdForUpdate(metricId)
        .orElseGet(() -> new MetricStatistics(metricId));

    List<DataPoint> history = new ArrayList<>(dataPointRepository
        .findByMetricIdOrderByTsDesc(metricId, PageRequest.of(0, REPLAY_LIMIT)));
    Collections.reverse(history);
    long totalPoints = dataPointRepository.countByMetricId(metricId);

    DetectorState state = DetectorState.empty();
    List<DetectionResult> results = new ArrayList<>();
    List<ChangedPoint> changedPoints = new ArrayList<>();
    int previousAnomalies = 0;
    int newAnomalies = 0;
    int changedCount = 0;
    for (DataPoint point : history) {
      DetectionResult result = detector.evaluate(newRule, state, point.getValue());
      state = detector.update(newRule, state, point.getValue());
      results.add(result);
      previousAnomalies += point.isAnomaly() ? 1 : 0;
      newAnomalies += result.anomaly() ? 1 : 0;
      if (result.anomaly() != point.isAnomaly()) {
        changedCount++;
        if (changedPoints.size() < MAX_CHANGED_POINTS) {
          changedPoints.add(new ChangedPoint(point.getTs(), point.getValue(), point.isAnomaly(),
              result.anomaly(), result.score()));
        }
      }
    }

    if (!dryRun) {
      apply(metric, newRule, stats, state, history, results);
    }
    return new RuleUpdateResponse(metricId, !dryRun, newRule.method(), new TreeMap<>(params),
        warmUpSamples, history.size(), totalPoints, totalPoints > history.size(),
        previousAnomalies, newAnomalies, changedCount, changedPoints);
  }

  private void apply(Metric metric, MetricRule newRule, MetricStatistics stats,
      DetectorState state, List<DataPoint> history, List<DetectionResult> results) {
    metric.updateRule(newRule.method(), newRule.params(), newRule.warmUpSamples());
    metricRepository.save(metric);
    if (!history.isEmpty()) {
      anomalyRepository.deleteByMetricIdFrom(metric.getId(), history.get(0).getTs());
    }
    List<Anomaly> anomalies = new ArrayList<>();
    for (int i = 0; i < history.size(); i++) {
      DataPoint point = history.get(i);
      DetectionResult result = results.get(i);
      point.setVerdict(result.anomaly(), result.score());
      if (result.anomaly()) {
        anomalies.add(new Anomaly(metric.getId(), point.getTs(), point.getValue(),
            result.score(), result.expectedLow(), result.expectedHigh(), newRule.method()));
      }
    }
    dataPointRepository.saveAll(history);
    anomalyRepository.saveAll(anomalies);
    stats.apply(state, stats.getLastTs());
    statisticsRepository.save(stats);
  }
}
