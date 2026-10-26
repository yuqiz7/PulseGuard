package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.dto.BucketSummary;
import edu.columbia.pulseguard.dto.MetricSummaryResponse;
import edu.columbia.pulseguard.model.DataPoint;
import edu.columbia.pulseguard.repository.DataPointRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI-generated: Claude (Anthropic)
/**
 * Implements getMetricSummary: statistics of a metric's points in a range, optionally per
 * hour or per day.
 */
@Service
public class MetricSummaryService {

  private final MetricLookupService lookupService;
  private final DataPointRepository dataPointRepository;

  /**
   * Creates the service.
   *
   * @param lookupService shared client ID and ownership checks
   * @param dataPointRepository data point access
   */
  public MetricSummaryService(MetricLookupService lookupService,
      DataPointRepository dataPointRepository) {
    this.lookupService = lookupService;
    this.dataPointRepository = dataPointRepository;
  }

  /**
   * Summarizes the points of a metric owned by the caller.
   *
   * @param clientId the X-Client-Id header value
   * @param metricId the metric ID
   * @param from inclusive start of the range
   * @param to inclusive end of the range
   * @param bucket optional bucket size: "hour"/"1h" or "day"/"1d"; null for none
   * @return overall and per-bucket statistics
   * @throws BadRequestException if the range or bucket size is invalid
   */
  @Transactional(readOnly = true)
  public MetricSummaryResponse summarize(String clientId, Long metricId, Instant from,
      Instant to, String bucket) {
    lookupService.requireOwnedMetric(clientId, metricId);
    RangeValidator.requireValidRange(from, to);
    ChronoUnit unit = parseBucket(bucket);
    List<DataPoint> points =
        dataPointRepository.findByMetricIdAndTsBetweenOrderByTsAsc(metricId, from, to);

    List<BucketSummary> buckets = new ArrayList<>();
    if (unit != null) {
      Map<Instant, List<DataPoint>> grouped = new LinkedHashMap<>();
      for (DataPoint point : points) {
        grouped.computeIfAbsent(point.getTs().truncatedTo(unit), key -> new ArrayList<>())
            .add(point);
      }
      for (Map.Entry<Instant, List<DataPoint>> entry : grouped.entrySet()) {
        buckets.add(new BucketSummary(entry.getKey(),
            SummaryCalculator.summarize(entry.getValue())));
      }
    }
    String bucketName = unit == null ? null : (unit == ChronoUnit.HOURS ? "hour" : "day");
    return new MetricSummaryResponse(metricId, from, to, SummaryCalculator.summarize(points),
        bucketName, buckets);
  }

  private static ChronoUnit parseBucket(String bucket) {
    if (bucket == null || bucket.isBlank()) {
      return null;
    }
    switch (bucket.trim().toLowerCase(Locale.ROOT)) {
      case "hour":
      case "1h":
        return ChronoUnit.HOURS;
      case "day":
      case "1d":
        return ChronoUnit.DAYS;
      default:
        throw new BadRequestException("bucket must be one of: hour, 1h, day, 1d");
    }
  }
}
