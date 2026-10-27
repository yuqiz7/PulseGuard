package edu.columbia.pulseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.api.ConflictException;
import edu.columbia.pulseguard.api.DataPointInput;
import edu.columbia.pulseguard.api.NotFoundException;
import edu.columbia.pulseguard.api.SubmitDataPointsRequest;
import edu.columbia.pulseguard.api.SubmitDataPointsResponse;
import edu.columbia.pulseguard.api.UnauthorizedException;
import edu.columbia.pulseguard.detection.AnomalyDetector;
import edu.columbia.pulseguard.detection.DetectionResult;
import edu.columbia.pulseguard.detection.DetectorState;
import edu.columbia.pulseguard.detection.MetricRule;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Unit tests for {@link DataIngestionService}, with repositories and the detection engine mocked.
 */
@ExtendWith(MockitoExtension.class)
class DataIngestionServiceTest {

  private static final Instant T0 = Instant.parse("2026-10-20T12:00:00Z");

  @Mock
  private MetricRepository metricRepository;
  @Mock
  private MetricStatisticsRepository statisticsRepository;
  @Mock
  private DataPointRepository dataPointRepository;
  @Mock
  private AnomalyRepository anomalyRepository;
  @Mock
  private AnomalyDetector detector;

  private DataIngestionService service;
  private MetricStatistics statistics;

  @BeforeEach
  void setUp() {
    service = new DataIngestionService(new MetricLookupService(metricRepository),
        statisticsRepository, dataPointRepository, anomalyRepository, detector);
    statistics = new MetricStatistics(7L);
  }

  private Metric givenMetric(DetectionMethod method, Map<String, Double> params) {
    Metric metric = new Metric("client-a", "latency", null, method, params, 2);
    ReflectionTestUtils.setField(metric, "id", 7L);
    when(metricRepository.findByIdAndClientId(7L, "client-a")).thenReturn(Optional.of(metric));
    when(statisticsRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(statistics));
    return metric;
  }

  private static SubmitDataPointsRequest batch(DataPointInput... points) {
    return new SubmitDataPointsRequest(List.of(points));
  }

  private static DataPointInput point(int secondsAfterStart, double value) {
    return new DataPointInput(T0.plusSeconds(secondsAfterStart), value);
  }

  /** Detector stub: counts samples, flags values above 100. */
  private void stubDetector() {
    when(detector.evaluate(any(MetricRule.class), any(DetectorState.class), anyDouble()))
        .thenAnswer(call -> {
          double value = call.getArgument(2);
          return value > 100
              ? new DetectionResult(true, value - 100, 0, 100)
              : new DetectionResult(false, 0, 0, 100);
        });
    when(detector.update(any(MetricRule.class), any(DetectorState.class), anyDouble()))
        .thenAnswer(call -> {
          DetectorState state = call.getArgument(1);
          return new DetectorState(state.sampleCount() + 1, state.ewma(), state.ewmaVariance(),
              state.window());
        });
  }

  @Test
  void submitStoresPointsAnomaliesAndStatistics() {
    givenMetric(DetectionMethod.THRESHOLD, Map.of("lowerBound", 0.0, "upperBound", 100.0));
    stubDetector();

    SubmitDataPointsResponse response = service.submit(" client-a", 7L,
        batch(point(0, 10), point(1, 150), point(2, 20)));

    assertThat(response.accepted()).isEqualTo(3);
    assertThat(response.anomalies()).isEqualTo(1);
    assertThat(response.results()).extracting("anomaly").containsExactly(false, true, false);
    assertThat(response.results().get(1).score()).isEqualTo(50.0);
    assertThat(response.results().get(1).expectedHigh()).isEqualTo(100.0);

    ArgumentCaptor<List<DataPoint>> stored = ArgumentCaptor.forClass(List.class);
    verify(dataPointRepository).saveAll(stored.capture());
    assertThat(stored.getValue()).extracting(DataPoint::getValue)
        .containsExactly(10.0, 150.0, 20.0);
    assertThat(stored.getValue()).extracting(DataPoint::isAnomaly).containsExactly(false, true,
        false);

    ArgumentCaptor<List<Anomaly>> flagged = ArgumentCaptor.forClass(List.class);
    verify(anomalyRepository).saveAll(flagged.capture());
    assertThat(flagged.getValue()).hasSize(1);
    assertThat(flagged.getValue().get(0).getTs()).isEqualTo(T0.plusSeconds(1));
    assertThat(flagged.getValue().get(0).getMetricId()).isEqualTo(7L);
    assertThat(flagged.getValue().get(0).getMethod()).isEqualTo(DetectionMethod.THRESHOLD);

    verify(statisticsRepository).save(statistics);
    assertThat(statistics.getSampleCount()).isEqualTo(3);
    assertThat(statistics.getLastTs()).isEqualTo(T0.plusSeconds(2));
  }

  @Test
  void eachPointIsEvaluatedBeforeItIsIncorporated() {
    givenMetric(DetectionMethod.EWMA, Map.of("alpha", 0.3, "tolerance", 3.0));
    stubDetector();

    service.submit("client-a", 7L, batch(point(0, 1), point(1, 2)));

    InOrder order = inOrder(detector);
    ArgumentCaptor<DetectorState> states = ArgumentCaptor.forClass(DetectorState.class);
    order.verify(detector).evaluate(any(), states.capture(), eq(1.0));
    order.verify(detector).update(any(), any(), eq(1.0));
    order.verify(detector).evaluate(any(), states.capture(), eq(2.0));
    order.verify(detector).update(any(), any(), eq(2.0));
    assertThat(states.getAllValues()).extracting(DetectorState::sampleCount)
        .containsExactly(0L, 1L);
  }

  @Test
  void noAnomaliesMeansNoAnomalyWrite() {
    givenMetric(DetectionMethod.THRESHOLD, Map.of("lowerBound", 0.0, "upperBound", 100.0));
    stubDetector();

    service.submit("client-a", 7L, batch(point(0, 1)));

    verify(anomalyRepository, never()).saveAll(any());
  }

  @Test
  void zscoreStartsFromStoredWindowOldestFirst() {
    givenMetric(DetectionMethod.ZSCORE, Map.of("windowSize", 3.0, "zThreshold", 3.0));
    stubDetector();
    statistics.apply(new DetectorState(5, null, null, List.of()), T0.minusSeconds(1));
    // The repository returns newest first.
    when(dataPointRepository.findByMetricIdOrderByTsDesc(eq(7L), any(Pageable.class)))
        .thenReturn(List.of(new DataPoint(7L, T0.minusSeconds(1), 3.0, false, 0),
            new DataPoint(7L, T0.minusSeconds(2), 2.0, false, 0),
            new DataPoint(7L, T0.minusSeconds(3), 1.0, false, 0)));

    service.submit("client-a", 7L, batch(point(0, 4)));

    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(dataPointRepository).findByMetricIdOrderByTsDesc(eq(7L), page.capture());
    assertThat(page.getValue().getPageSize()).isEqualTo(3);
    ArgumentCaptor<DetectorState> state = ArgumentCaptor.forClass(DetectorState.class);
    verify(detector).evaluate(any(), state.capture(), eq(4.0));
    assertThat(state.getValue().window()).containsExactly(1.0, 2.0, 3.0);
    assertThat(state.getValue().sampleCount()).isEqualTo(5);
  }

  @Test
  void otherMethodsDoNotLoadWindow() {
    givenMetric(DetectionMethod.EWMA, Map.of("alpha", 0.3, "tolerance", 3.0));
    stubDetector();

    service.submit("client-a", 7L, batch(point(0, 1)));

    verify(dataPointRepository, never()).findByMetricIdOrderByTsDesc(any(), any());
  }

  @Test
  void timestampNotAfterLastStoredPointIsConflict() {
    givenMetric(DetectionMethod.EWMA, Map.of("alpha", 0.3, "tolerance", 3.0));
    statistics.apply(DetectorState.empty(), T0);

    for (int offset : new int[] {-5, 0}) {
      assertThatThrownBy(() -> service.submit("client-a", 7L, batch(point(offset, 1))))
          .isInstanceOf(ConflictException.class);
    }
    verifyNothingWritten();
  }

  @Test
  void outOfOrderOrDuplicateTimestampInsideBatchIsConflict() {
    givenMetric(DetectionMethod.EWMA, Map.of("alpha", 0.3, "tolerance", 3.0));

    assertThatThrownBy(() -> service.submit("client-a", 7L,
        batch(point(1, 1), point(0, 2))))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.submit("client-a", 7L,
        batch(point(1, 1), point(1, 2))))
        .isInstanceOf(ConflictException.class);
    verifyNothingWritten();
  }

  @Test
  void firstPointOfNewMetricIsAcceptedAtAnyTime() {
    givenMetric(DetectionMethod.THRESHOLD, Map.of("lowerBound", 0.0, "upperBound", 100.0));
    stubDetector();

    assertThat(service.submit("client-a", 7L, batch(point(-1000, 1))).accepted()).isEqualTo(1);
  }

  @Test
  void nonFiniteValueIsBadRequestAndNothingIsStored() {
    givenMetric(DetectionMethod.EWMA, Map.of("alpha", 0.3, "tolerance", 3.0));

    assertThatThrownBy(() -> service.submit("client-a", 7L,
        batch(point(0, 1), point(1, Double.POSITIVE_INFINITY))))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.submit("client-a", 7L, batch(point(0, Double.NaN))))
        .isInstanceOf(BadRequestException.class);
    verifyNothingWritten();
  }

  @Test
  void unknownOrForeignMetricIsNotFound() {
    when(metricRepository.findByIdAndClientId(7L, "client-b")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit("client-b", 7L, batch(point(0, 1))))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(statisticsRepository, detector);
    verifyNothingWritten();
  }

  @Test
  void missingClientIdIsUnauthorized() {
    assertThatThrownBy(() -> service.submit(null, 7L, batch(point(0, 1))))
        .isInstanceOf(UnauthorizedException.class);
    verifyNoInteractions(statisticsRepository, detector);
  }

  @Test
  void missingStatisticsRowIsAnInternalError() {
    Metric metric = new Metric("client-a", "m", null, DetectionMethod.EWMA, Map.of(), 0);
    when(metricRepository.findByIdAndClientId(7L, "client-a")).thenReturn(Optional.of(metric));
    when(statisticsRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit("client-a", 7L, batch(point(0, 1))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void statisticsRowIsLockedBeforeAnythingIsRead() {
    givenMetric(DetectionMethod.ZSCORE, Map.of("windowSize", 3.0, "zThreshold", 3.0));
    stubDetector();
    when(dataPointRepository.findByMetricIdOrderByTsDesc(eq(7L), any(Pageable.class)))
        .thenReturn(List.of());

    service.submit("client-a", 7L, batch(point(0, 1)));

    InOrder order = inOrder(statisticsRepository, dataPointRepository);
    order.verify(statisticsRepository).findByIdForUpdate(7L);
    order.verify(dataPointRepository).findByMetricIdOrderByTsDesc(eq(7L), any(Pageable.class));
  }

  private void verifyNothingWritten() {
    verify(dataPointRepository, never()).saveAll(any());
    verify(anomalyRepository, never()).saveAll(any());
    verify(statisticsRepository, never()).save(any());
  }
}
