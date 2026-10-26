package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.api.ConflictException;
import edu.columbia.pulseguard.detection.AnomalyDetector;
import edu.columbia.pulseguard.detection.DetectionEngine;
import edu.columbia.pulseguard.detection.DetectionResult;
import edu.columbia.pulseguard.detection.DetectorState;
import edu.columbia.pulseguard.detection.MetricRule;
import edu.columbia.pulseguard.dto.DataPointInput;
import edu.columbia.pulseguard.dto.PointResult;
import edu.columbia.pulseguard.dto.SubmitDataPointsResponse;
import edu.columbia.pulseguard.model.Anomaly;
import edu.columbia.pulseguard.model.DataPoint;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.model.MetricStatistics;
import edu.columbia.pulseguard.repository.AnomalyRepository;
import edu.columbia.pulseguard.repository.DataPointRepository;
import edu.columbia.pulseguard.repository.MetricStatisticsRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI-generated: Claude (Anthropic)
/**
 * Implements submitDataPoints: judges each point, stores points, anomalies and statistics.
 *
 * <p>The metric's statistics row is locked for the whole transaction, so concurrent batches
 * for the same metric are processed one after another and never corrupt the statistics.
 */
@Service
public class DataIngestionService {

  private final MetricLookupService lookupService;
  private final DataPointRepository dataPointRepository;
  private final AnomalyRepository anomalyRepository;
  private final MetricStatisticsRepository statisticsRepository;
  private final AnomalyDetector detector;

  /**
   * Creates the service.
   *
   * @param lookupService shared client ID and ownership checks
   * @param dataPointRepository data point access
   * @param anomalyRepository anomaly access
   * @param statisticsRepository statistics access (with row locking)
   * @param detector the detection engine
   */
  public DataIngestionService(MetricLookupService lookupService,
      DataPointRepository dataPointRepository, AnomalyRepository anomalyRepository,
      MetricStatisticsRepository statisticsRepository, AnomalyDetector detector) {
    this.lookupService = lookupService;
    this.dataPointRepository = dataPointRepository;
    this.anomalyRepository = anomalyRepository;
    this.statisticsRepository = statisticsRepository;
    this.detector = detector;
  }

  /**
   * Judges and stores a batch of points for a metric owned by the caller.
   *
   * @param clientId the X-Client-Id header value
   * @param metricId the metric ID
   * @param points the submitted points, in any order
   * @return the verdict for every point, oldest first
   * @throws edu.columbia.pulseguard.api.NotFoundException if the metric is not the caller's
   * @throws ConflictException if a timestamp is repeated or not after the latest stored point
   */
  @Transactional
  public SubmitDataPointsResponse submit(String clientId, Long metricId,
      List<DataPointInput> points) {
    final Metric metric = lookupService.requireOwnedMetric(clientId, metricId);
    MetricStatistics stats = statisticsRepository.findByIdForUpdate(metricId)
        .orElseGet(() -> new MetricStatistics(metricId));
    List<DataPointInput> ordered = new ArrayList<>(points);
    ordered.sort(Comparator.comparing(DataPointInput::timestamp));
    final Instant latest = checkStrictlyIncreasing(ordered, stats.getLastTs());

    MetricRule rule = MetricRule.fromMetric(metric);
    DetectorState state = stats.toDetectorState(
        recentValues(metricId, DetectionEngine.windowSize(rule)));
    List<DataPoint> storedPoints = new ArrayList<>();
    List<Anomaly> anomalies = new ArrayList<>();
    List<PointResult> results = new ArrayList<>();
    for (DataPointInput point : ordered) {
      double value = point.value();
      DetectionResult result = detector.evaluate(rule, state, value);
      state = detector.update(rule, state, value);
      storedPoints.add(
          new DataPoint(metricId, point.timestamp(), value, result.anomaly(), result.score()));
      if (result.anomaly()) {
        anomalies.add(new Anomaly(metricId, point.timestamp(), value, result.score(),
            result.expectedLow(), result.expectedHigh(), metric.getMethod()));
      }
      results.add(new PointResult(point.timestamp(), value, result.anomaly(), result.score(),
          result.expectedLow(), result.expectedHigh()));
    }

    dataPointRepository.saveAll(storedPoints);
    anomalyRepository.saveAll(anomalies);
    stats.apply(state, latest);
    statisticsRepository.save(stats);
    return new SubmitDataPointsResponse(metricId, results.size(), anomalies.size(), results);
  }

  /**
   * Checks that sorted timestamps are unique and after the latest stored timestamp.
   *
   * @return the latest timestamp of the batch
   */
  private static Instant checkStrictlyIncreasing(List<DataPointInput> ordered,
      Instant lastStored) {
    Instant previous = lastStored;
    for (DataPointInput point : ordered) {
      if (previous != null && !point.timestamp().isAfter(previous)) {
        throw new ConflictException("Timestamp " + point.timestamp()
            + " must be after the latest stored or submitted timestamp " + previous);
      }
      previous = point.timestamp();
    }
    return previous;
  }

  private List<Double> recentValues(Long metricId, int windowSize) {
    List<DataPoint> newestFirst =
        dataPointRepository.findByMetricIdOrderByTsDesc(metricId, PageRequest.of(0, windowSize));
    List<Double> values = new ArrayList<>();
    for (DataPoint point : newestFirst) {
      values.add(point.getValue());
    }
    Collections.reverse(values);
    return values;
  }
}
