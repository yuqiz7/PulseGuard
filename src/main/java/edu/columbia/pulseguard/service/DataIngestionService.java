package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.api.ConflictException;
import edu.columbia.pulseguard.api.DataPointInput;
import edu.columbia.pulseguard.api.NotFoundException;
import edu.columbia.pulseguard.api.PointVerdict;
import edu.columbia.pulseguard.api.SubmitDataPointsRequest;
import edu.columbia.pulseguard.api.SubmitDataPointsResponse;
import edu.columbia.pulseguard.detection.AnomalyDetector;
import edu.columbia.pulseguard.detection.DetectionResult;
import edu.columbia.pulseguard.detection.DetectorState;
import edu.columbia.pulseguard.detection.MetricRule;
import edu.columbia.pulseguard.detection.RuleParams;
import edu.columbia.pulseguard.model.Anomaly;
import edu.columbia.pulseguard.model.DataPoint;
import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.model.MetricStatistics;
import edu.columbia.pulseguard.repository.AnomalyRepository;
import edu.columbia.pulseguard.repository.DataPointRepository;
import edu.columbia.pulseguard.repository.MetricStatisticsRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Implements submitDataPoints: checks ownership and ordering, runs the detection engine on every
 * point and stores the points, the anomalies and the updated statistics in one transaction.
 *
 * <p>The metric's statistics row is locked for the whole batch (PESSIMISTIC_WRITE), so concurrent
 * batches for the same metric are processed one at a time.
 */
@Service
public class DataIngestionService {

  private final MetricLookupService lookupService;
  private final MetricStatisticsRepository statisticsRepository;
  private final DataPointRepository dataPointRepository;
  private final AnomalyRepository anomalyRepository;
  private final AnomalyDetector detector;

  /**
   * Creates the service.
   *
   * @param lookupService client ID validation and ownership checks
   * @param statisticsRepository statistics data access (with row lock)
   * @param dataPointRepository data point data access
   * @param anomalyRepository anomaly data access
   * @param detector the detection engine
   */
  public DataIngestionService(MetricLookupService lookupService,
      MetricStatisticsRepository statisticsRepository, DataPointRepository dataPointRepository,
      AnomalyRepository anomalyRepository, AnomalyDetector detector) {
    this.lookupService = lookupService;
    this.statisticsRepository = statisticsRepository;
    this.dataPointRepository = dataPointRepository;
    this.anomalyRepository = anomalyRepository;
    this.detector = detector;
  }

  /**
   * Ingests a batch of points. Either the whole batch is stored or, on an error, nothing is.
   *
   * @param clientId the X-Client-Id header value
   * @param metricId the metric ID from the URL
   * @param request the validated request body
   * @return one verdict per point
   * @throws edu.columbia.pulseguard.api.UnauthorizedException if the client ID is missing
   * @throws NotFoundException if the metric does not exist or belongs to another client
   * @throws BadRequestException if a value is not a finite number
   * @throws ConflictException if a timestamp is not strictly later than its predecessor or than
   *     the latest stored point
   */
  @Transactional
  public SubmitDataPointsResponse submit(String clientId, Long metricId,
      SubmitDataPointsRequest request) {
    Metric metric = lookupService.requireOwnedMetric(clientId, metricId);
    MetricStatistics statistics = statisticsRepository.findByIdForUpdate(metricId)
        .orElseThrow(() -> new IllegalStateException(
            "Statistics row missing for metric " + metricId));
    List<DataPointInput> points = request.dataPoints();
    validateBatch(points, statistics.getLastTs());

    MetricRule rule = MetricRule.fromMetric(metric);
    DetectorState state = statistics.toDetectorState(recentValues(metric, rule));

    List<DataPoint> dataPoints = new ArrayList<>();
    List<Anomaly> anomalies = new ArrayList<>();
    List<PointVerdict> verdicts = new ArrayList<>();
    for (DataPointInput point : points) {
      double value = point.value();
      DetectionResult result = detector.evaluate(rule, state, value);
      state = detector.update(rule, state, value);

      dataPoints.add(new DataPoint(metricId, point.timestamp(), value, result.anomaly(),
          result.score()));
      if (result.anomaly()) {
        anomalies.add(new Anomaly(metricId, point.timestamp(), value, result.score(),
            result.expectedLow(), result.expectedHigh(), metric.getMethod()));
      }
      verdicts.add(new PointVerdict(point.timestamp(), value, result.anomaly(), result.score(),
          result.expectedLow(), result.expectedHigh()));
    }

    dataPointRepository.saveAll(dataPoints);
    if (!anomalies.isEmpty()) {
      anomalyRepository.saveAll(anomalies);
    }
    statistics.apply(state, points.get(points.size() - 1).timestamp());
    statisticsRepository.save(statistics);
    return new SubmitDataPointsResponse(dataPoints.size(), anomalies.size(), verdicts);
  }

  private static void validateBatch(List<DataPointInput> points, Instant lastStored) {
    Instant previous = lastStored;
    for (DataPointInput point : points) {
      if (!Double.isFinite(point.value())) {
        throw new BadRequestException("Value at " + point.timestamp() + " must be a finite number");
      }
      if (previous != null && !point.timestamp().isAfter(previous)) {
        throw new ConflictException("Timestamp " + point.timestamp()
            + " is not later than " + previous + "; points must arrive in increasing order");
      }
      previous = point.timestamp();
    }
  }

  /** Loads the stored values the z-score window needs; other methods keep no window. */
  private List<Double> recentValues(Metric metric, MetricRule rule) {
    if (metric.getMethod() != DetectionMethod.ZSCORE) {
      return List.of();
    }
    int windowSize = (int) rule.param(RuleParams.WINDOW_SIZE, RuleParams.DEFAULT_WINDOW_SIZE);
    List<Double> values = new ArrayList<>();
    for (DataPoint stored : dataPointRepository.findByMetricIdOrderByTsDesc(metric.getId(),
        PageRequest.of(0, windowSize))) {
      values.add(stored.getValue());
    }
    Collections.reverse(values);
    return values;
  }
}
