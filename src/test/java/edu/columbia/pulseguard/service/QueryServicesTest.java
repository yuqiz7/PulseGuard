package edu.columbia.pulseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.api.NotFoundException;
import edu.columbia.pulseguard.dto.AnomalyQueryResponse;
import edu.columbia.pulseguard.dto.MetricSummaryResponse;
import edu.columbia.pulseguard.model.Anomaly;
import edu.columbia.pulseguard.model.DataPoint;
import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.repository.AnomalyRepository;
import edu.columbia.pulseguard.repository.DataPointRepository;
import edu.columbia.pulseguard.repository.MetricRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

// AI-generated: Claude (Anthropic)
/**
 * Unit tests for {@link AnomalyQueryService} and {@link MetricSummaryService}, with
 * repositories mocked.
 */
@ExtendWith(MockitoExtension.class)
class QueryServicesTest {

  private static final Long METRIC_ID = 4L;
  private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");

  @Mock
  private MetricRepository metricRepository;

  @Mock
  private AnomalyRepository anomalyRepository;

  @Mock
  private DataPointRepository dataPointRepository;

  private AnomalyQueryService anomalyQueryService;

  private MetricSummaryService summaryService;

  @BeforeEach
  void setUp() {
    MetricLookupService lookup = new MetricLookupService(metricRepository);
    anomalyQueryService = new AnomalyQueryService(lookup, anomalyRepository);
    summaryService = new MetricSummaryService(lookup, dataPointRepository);
  }

  private void givenOwnedMetric() {
    Metric metric = new Metric("c", "cpu", null, DetectionMethod.ZSCORE, Map.of(), 0);
    when(metricRepository.findByIdAndClientId(METRIC_ID, "c")).thenReturn(Optional.of(metric));
  }

  @Test
  void queryReturnsAnomaliesAndTotal() {
    givenOwnedMetric();
    Anomaly anomaly = new Anomaly(METRIC_ID, FROM.plusSeconds(60), 50.0, 4.0, 1.0, 9.0,
        DetectionMethod.ZSCORE);
    when(anomalyRepository.findByMetricIdAndTsBetweenAndScoreGreaterThanEqualOrderByTsAsc(
        eq(METRIC_ID), eq(FROM), eq(TO), eq(3.0), any(Pageable.class)))
        .thenReturn(List.of(anomaly));
    when(anomalyRepository.countByMetricIdAndTsBetweenAndScoreGreaterThanEqual(
        METRIC_ID, FROM, TO, 3.0)).thenReturn(7L);

    AnomalyQueryResponse response = anomalyQueryService.query("c", METRIC_ID, FROM, TO, 3.0, 1);

    assertThat(response.total()).isEqualTo(7);
    assertThat(response.anomalies()).hasSize(1);
    assertThat(response.anomalies().get(0).value()).isEqualTo(50.0);
    assertThat(response.anomalies().get(0).method()).isEqualTo(DetectionMethod.ZSCORE);
  }

  @Test
  void queryRejectsInvertedRange() {
    givenOwnedMetric();

    assertThatThrownBy(() -> anomalyQueryService.query("c", METRIC_ID, TO, FROM, 0, 10))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("from must not be after to");
  }

  @Test
  void queryRejectsBadLimitAndScore() {
    givenOwnedMetric();

    assertThatThrownBy(() -> anomalyQueryService.query("c", METRIC_ID, FROM, TO, 0, 0))
        .hasMessageContaining("limit");
    assertThatThrownBy(() -> anomalyQueryService.query("c", METRIC_ID, FROM, TO, 0, 1001))
        .hasMessageContaining("limit");
    assertThatThrownBy(() -> anomalyQueryService.query("c", METRIC_ID, FROM, TO, -1, 10))
        .hasMessageContaining("minScore");
  }

  @Test
  void queryOnOtherClientsMetricIsNotFound() {
    when(metricRepository.findByIdAndClientId(METRIC_ID, "x")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> anomalyQueryService.query("x", METRIC_ID, FROM, TO, 0, 10))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void summaryGroupsPointsIntoHourlyBuckets() {
    givenOwnedMetric();
    when(dataPointRepository.findByMetricIdAndTsBetweenOrderByTsAsc(METRIC_ID, FROM, TO))
        .thenReturn(List.of(
            new DataPoint(METRIC_ID, FROM.plusSeconds(60), 1.0, false, 0),
            new DataPoint(METRIC_ID, FROM.plusSeconds(120), 3.0, true, 5),
            new DataPoint(METRIC_ID, FROM.plusSeconds(3700), 10.0, false, 0)));

    MetricSummaryResponse response = summaryService.summarize("c", METRIC_ID, FROM, TO, "1h");

    assertThat(response.bucket()).isEqualTo("hour");
    assertThat(response.overall().count()).isEqualTo(3);
    assertThat(response.overall().anomalies()).isEqualTo(1);
    assertThat(response.buckets()).hasSize(2);
    assertThat(response.buckets().get(0).start()).isEqualTo(FROM);
    assertThat(response.buckets().get(0).stats().mean()).isEqualTo(2.0);
    assertThat(response.buckets().get(1).start()).isEqualTo(FROM.plusSeconds(3600));
  }

  @Test
  void summaryWithDayBucketAndNoBucket() {
    givenOwnedMetric();
    when(dataPointRepository.findByMetricIdAndTsBetweenOrderByTsAsc(METRIC_ID, FROM, TO))
        .thenReturn(List.of(new DataPoint(METRIC_ID, FROM.plusSeconds(60), 1.0, false, 0)));

    MetricSummaryResponse daily = summaryService.summarize("c", METRIC_ID, FROM, TO, "DAY");
    MetricSummaryResponse plain = summaryService.summarize("c", METRIC_ID, FROM, TO, null);

    assertThat(daily.bucket()).isEqualTo("day");
    assertThat(daily.buckets()).hasSize(1);
    assertThat(plain.bucket()).isNull();
    assertThat(plain.buckets()).isEmpty();
  }

  @Test
  void summaryRejectsUnknownBucket() {
    givenOwnedMetric();

    assertThatThrownBy(() -> summaryService.summarize("c", METRIC_ID, FROM, TO, "week"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("bucket");
  }
}
