package edu.columbia.pulseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.columbia.pulseguard.api.ConflictException;
import edu.columbia.pulseguard.api.NotFoundException;
import edu.columbia.pulseguard.detection.DetectionEngine;
import edu.columbia.pulseguard.detection.DetectorState;
import edu.columbia.pulseguard.detection.RuleParams;
import edu.columbia.pulseguard.dto.DataPointInput;
import edu.columbia.pulseguard.dto.SubmitDataPointsResponse;
import edu.columbia.pulseguard.model.Anomaly;
import edu.columbia.pulseguard.model.DataPoint;
import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.model.MetricStatistics;
import edu.columbia.pulseguard.repository.AnomalyRepository;
import edu.columbia.pulseguard.repository.DataPointRepository;
import edu.columbia.pulseguard.repository.MetricRepository;
import edu.columbia.pulseguard.repository.MetricStatisticsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

// AI-generated: Claude (Anthropic)
/**
 * Unit tests for {@link DataIngestionService}: repositories are mocked, the real detection
 * engine is used so the expected verdicts can be checked.
 */
@ExtendWith(MockitoExtension.class)
class DataIngestionServiceTest {

  private static final Long METRIC_ID = 7L;
  private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

  @Mock
  private MetricRepository metricRepository;

  @Mock
  private DataPointRepository dataPointRepository;

  @Mock
  private AnomalyRepository anomalyRepository;

  @Mock
  private MetricStatisticsRepository statisticsRepository;

  private DataIngestionService service;

  private MetricStatistics stats;

  @BeforeEach
  void setUp() {
    service = new DataIngestionService(new MetricLookupService(metricRepository),
        dataPointRepository, anomalyRepository, statisticsRepository, new DetectionEngine());
    stats = new MetricStatistics(METRIC_ID);
  }

  private void givenThresholdMetric() {
    Metric metric = new Metric("c", "temp", "C", DetectionMethod.THRESHOLD,
        Map.of(RuleParams.LOWER_BOUND, 0.0, RuleParams.UPPER_BOUND, 30.0), 0);
    when(metricRepository.findByIdAndClientId(METRIC_ID, "c")).thenReturn(Optional.of(metric));
    when(statisticsRepository.findByIdForUpdate(METRIC_ID)).thenReturn(Optional.of(stats));
  }

  private static DataPointInput point(long secondsAfterStart, double value) {
    return new DataPointInput(T0.plusSeconds(secondsAfterStart), value);
  }

  @Test
  void flagsAndStoresAnomaliesInTimestampOrder() {
    givenThresholdMetric();

    SubmitDataPointsResponse response = service.submit("c", METRIC_ID,
        List.of(point(20, 35.0), point(10, 20.0), point(30, -5.0)));

    assertThat(response.accepted()).isEqualTo(3);
    assertThat(response.anomalies()).isEqualTo(2);
    assertThat(response.results()).extracting(r -> r.timestamp())
        .containsExactly(T0.plusSeconds(10), T0.plusSeconds(20), T0.plusSeconds(30));
    assertThat(response.results()).extracting(r -> r.anomaly())
        .containsExactly(false, true, true);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Anomaly>> anomalies = ArgumentCaptor.forClass(List.class);
    verify(anomalyRepository).saveAll(anomalies.capture());
    assertThat(anomalies.getValue()).extracting(Anomaly::getScore).containsExactly(5.0, 5.0);
    verify(dataPointRepository).saveAll(anyList());
  }

  @Test
  void updatesStatisticsWithLatestTimestamp() {
    givenThresholdMetric();

    service.submit("c", METRIC_ID, List.of(point(10, 1.0), point(20, 2.0)));

    assertThat(stats.getSampleCount()).isEqualTo(2);
    assertThat(stats.getLastTs()).isEqualTo(T0.plusSeconds(20));
    verify(statisticsRepository).save(stats);
  }

  @Test
  void loadsRecentWindowForZscore() {
    Metric metric = new Metric("c", "cpu", null, DetectionMethod.ZSCORE,
        Map.of(RuleParams.WINDOW_SIZE, 4.0, RuleParams.Z_THRESHOLD, 2.0), 0);
    when(metricRepository.findByIdAndClientId(METRIC_ID, "c")).thenReturn(Optional.of(metric));
    stats.apply(new DetectorState(4, null, null, List.of()), T0);
    when(statisticsRepository.findByIdForUpdate(METRIC_ID)).thenReturn(Optional.of(stats));
    when(dataPointRepository.findByMetricIdOrderByTsDesc(eq(METRIC_ID), any(Pageable.class)))
        .thenReturn(List.of(
            new DataPoint(METRIC_ID, T0, 12.0, false, 0),
            new DataPoint(METRIC_ID, T0.minusSeconds(1), 10.0, false, 0),
            new DataPoint(METRIC_ID, T0.minusSeconds(2), 12.0, false, 0),
            new DataPoint(METRIC_ID, T0.minusSeconds(3), 10.0, false, 0)));

    SubmitDataPointsResponse response = service.submit("c", METRIC_ID,
        List.of(point(1, 11.0), point(2, 30.0)));

    assertThat(response.results()).extracting(r -> r.anomaly()).containsExactly(false, true);
  }

  @Test
  void rejectsTimestampNotAfterLatestStoredPoint() {
    givenThresholdMetric();
    stats.apply(DetectorState.empty(), T0.plusSeconds(100));

    assertThatThrownBy(() -> service.submit("c", METRIC_ID, List.of(point(100, 1.0))))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("must be after");
    verify(dataPointRepository, never()).saveAll(anyList());
  }

  @Test
  void rejectsDuplicateTimestampsInBatch() {
    givenThresholdMetric();

    assertThatThrownBy(() -> service.submit("c", METRIC_ID,
        List.of(point(10, 1.0), point(10, 2.0))))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void createsStatisticsWhenMissing() {
    Metric metric = new Metric("c", "temp", null, DetectionMethod.THRESHOLD,
        Map.of(RuleParams.UPPER_BOUND, 30.0), 0);
    when(metricRepository.findByIdAndClientId(METRIC_ID, "c")).thenReturn(Optional.of(metric));
    when(statisticsRepository.findByIdForUpdate(METRIC_ID)).thenReturn(Optional.empty());

    service.submit("c", METRIC_ID, List.of(point(10, 1.0)));

    verify(statisticsRepository).save(any(MetricStatistics.class));
  }

  @Test
  void otherClientsMetricIsNotFound() {
    when(metricRepository.findByIdAndClientId(METRIC_ID, "intruder")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit("intruder", METRIC_ID, List.of(point(1, 1.0))))
        .isInstanceOf(NotFoundException.class);
  }
}
