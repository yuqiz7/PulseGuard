package edu.columbia.pulseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.detection.DetectionEngine;
import edu.columbia.pulseguard.detection.RuleParams;
import edu.columbia.pulseguard.dto.RuleUpdateResponse;
import edu.columbia.pulseguard.dto.UpdateRuleRequest;
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
 * Unit tests for {@link RuleUpdateService}: repositories are mocked, the real detection engine
 * replays the history.
 */
@ExtendWith(MockitoExtension.class)
class RuleUpdateServiceTest {

  private static final Long METRIC_ID = 9L;
  private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

  @Mock
  private MetricRepository metricRepository;

  @Mock
  private DataPointRepository dataPointRepository;

  @Mock
  private AnomalyRepository anomalyRepository;

  @Mock
  private MetricStatisticsRepository statisticsRepository;

  private RuleUpdateService service;

  private Metric metric;

  private List<DataPoint> newestFirst;

  @BeforeEach
  void setUp() {
    service = new RuleUpdateService(new MetricLookupService(metricRepository), metricRepository,
        dataPointRepository, anomalyRepository, statisticsRepository, new DetectionEngine());
    metric = new Metric("c", "temp", null, DetectionMethod.THRESHOLD,
        Map.of(RuleParams.UPPER_BOUND, 50.0), 0);
    newestFirst = List.of(
        new DataPoint(METRIC_ID, T0.plusSeconds(30), 60.0, true, 10.0),
        new DataPoint(METRIC_ID, T0.plusSeconds(20), 40.0, false, 0.0),
        new DataPoint(METRIC_ID, T0.plusSeconds(10), 20.0, false, 0.0));
    when(metricRepository.findByIdAndClientId(METRIC_ID, "c")).thenReturn(Optional.of(metric));
  }

  private void givenHistory(long totalPoints) {
    when(statisticsRepository.findByIdForUpdate(METRIC_ID))
        .thenReturn(Optional.of(new MetricStatistics(METRIC_ID)));
    when(dataPointRepository.findByMetricIdOrderByTsDesc(eq(METRIC_ID), any(Pageable.class)))
        .thenReturn(newestFirst);
    when(dataPointRepository.countByMetricId(METRIC_ID)).thenReturn(totalPoints);
  }

  private static UpdateRuleRequest lowerThreshold() {
    return new UpdateRuleRequest(DetectionMethod.THRESHOLD,
        Map.of(RuleParams.UPPER_BOUND, 30.0), 0);
  }

  @Test
  void dryRunReportsChangesWithoutSaving() {
    givenHistory(3);

    RuleUpdateResponse response = service.update("c", METRIC_ID, lowerThreshold(), true);

    assertThat(response.applied()).isFalse();
    assertThat(response.replayedPoints()).isEqualTo(3);
    assertThat(response.truncated()).isFalse();
    assertThat(response.previousAnomalyCount()).isEqualTo(1);
    assertThat(response.newAnomalyCount()).isEqualTo(2);
    assertThat(response.changedPointCount()).isEqualTo(1);
    assertThat(response.changedPoints().get(0).timestamp()).isEqualTo(T0.plusSeconds(20));
    assertThat(metric.getParams()).containsEntry(RuleParams.UPPER_BOUND, 50.0);
    verify(metricRepository, never()).save(any());
    verify(anomalyRepository, never()).deleteByMetricIdFrom(anyLong(), any());
  }

  @Test
  void applyingSavesRuleVerdictsAndAnomalies() {
    givenHistory(3);

    RuleUpdateResponse response = service.update("c", METRIC_ID, lowerThreshold(), false);

    assertThat(response.applied()).isTrue();
    assertThat(metric.getParams()).containsEntry(RuleParams.UPPER_BOUND, 30.0);
    assertThat(newestFirst.get(1).isAnomaly()).isTrue();
    verify(metricRepository).save(metric);
    verify(anomalyRepository).deleteByMetricIdFrom(metric.getId(), T0.plusSeconds(10));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Anomaly>> saved = ArgumentCaptor.forClass(List.class);
    verify(anomalyRepository).saveAll(saved.capture());
    assertThat(saved.getValue()).extracting(Anomaly::getValue).containsExactly(40.0, 60.0);
    verify(dataPointRepository).saveAll(anyList());
  }

  @Test
  void reportsTruncationWhenHistoryExceedsReplayLimit() {
    givenHistory(RuleUpdateService.REPLAY_LIMIT + 5L);

    RuleUpdateResponse response = service.update("c", METRIC_ID, lowerThreshold(), true);

    assertThat(response.truncated()).isTrue();
    assertThat(response.totalPoints()).isEqualTo(RuleUpdateService.REPLAY_LIMIT + 5L);
  }

  @Test
  void switchingMethodAppliesDefaults() {
    givenHistory(3);

    RuleUpdateResponse response = service.update("c", METRIC_ID,
        new UpdateRuleRequest(DetectionMethod.EWMA, null, null), true);

    assertThat(response.method()).isEqualTo(DetectionMethod.EWMA);
    assertThat(response.params()).containsKeys(RuleParams.ALPHA, RuleParams.TOLERANCE);
    assertThat(response.warmUpSamples()).isEqualTo(RuleParams.DEFAULT_WARM_UP_SAMPLES);
    assertThat(response.newAnomalyCount()).isZero();
  }

  @Test
  void invalidRuleIsRejected() {
    assertThatThrownBy(() -> service.update("c", METRIC_ID,
        new UpdateRuleRequest(DetectionMethod.THRESHOLD, Map.of(), null), true))
        .isInstanceOf(BadRequestException.class);
  }
}
