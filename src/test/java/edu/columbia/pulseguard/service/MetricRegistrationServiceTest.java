package edu.columbia.pulseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.api.ConflictException;
import edu.columbia.pulseguard.api.RegisterMetricRequest;
import edu.columbia.pulseguard.api.UnauthorizedException;
import edu.columbia.pulseguard.detection.RuleParams;
import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.model.MetricStatistics;
import edu.columbia.pulseguard.repository.MetricRepository;
import edu.columbia.pulseguard.repository.MetricStatisticsRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Unit tests for {@link MetricRegistrationService}, with the repositories mocked.
 */
@ExtendWith(MockitoExtension.class)
class MetricRegistrationServiceTest {

  @Mock
  private MetricRepository metricRepository;
  @Mock
  private MetricStatisticsRepository statisticsRepository;

  private MetricRegistrationService service;

  @BeforeEach
  void setUp() {
    service = new MetricRegistrationService(metricRepository, statisticsRepository,
        new MetricLookupService(metricRepository));
  }

  private static RegisterMetricRequest request(String name, DetectionMethod method,
      Map<String, Double> params, Integer warmUp) {
    return new RegisterMetricRequest(name, "ms", method, params, warmUp);
  }

  @Test
  void registerStoresTrimmedMetricWithDefaultsAndEmptyStatistics() {
    when(metricRepository.existsByClientIdAndName("client-a", "latency")).thenReturn(false);
    when(metricRepository.saveAndFlush(any(Metric.class))).thenAnswer(call -> call.getArgument(0));

    Metric saved = service.register(" client-a ", request(" latency ", DetectionMethod.ZSCORE,
        null, null));

    ArgumentCaptor<Metric> metric = ArgumentCaptor.forClass(Metric.class);
    verify(metricRepository).saveAndFlush(metric.capture());
    assertThat(saved).isSameAs(metric.getValue());
    assertThat(saved.getClientId()).isEqualTo("client-a");
    assertThat(saved.getName()).isEqualTo("latency");
    assertThat(saved.getUnit()).isEqualTo("ms");
    assertThat(saved.getMethod()).isEqualTo(DetectionMethod.ZSCORE);
    assertThat(saved.getParams()).containsEntry(RuleParams.WINDOW_SIZE, 30.0)
        .containsEntry(RuleParams.Z_THRESHOLD, 3.0);
    assertThat(saved.getWarmUpSamples()).isEqualTo(RuleParams.DEFAULT_WARM_UP_SAMPLES);

    ArgumentCaptor<MetricStatistics> stats = ArgumentCaptor.forClass(MetricStatistics.class);
    verify(statisticsRepository).save(stats.capture());
    assertThat(stats.getValue().getSampleCount()).isZero();
    assertThat(stats.getValue().getLastTs()).isNull();
  }

  @Test
  void registerRejectsDuplicateNameWithoutSaving() {
    when(metricRepository.existsByClientIdAndName("client-a", "latency")).thenReturn(true);

    assertThatThrownBy(() -> service.register("client-a",
        request("latency", DetectionMethod.EWMA, null, null)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("latency");
    verify(metricRepository, never()).saveAndFlush(any());
    verifyNoInteractions(statisticsRepository);
  }

  @Test
  void registerMapsRacingInsertToConflict() {
    when(metricRepository.existsByClientIdAndName("client-a", "latency")).thenReturn(false);
    when(metricRepository.saveAndFlush(any(Metric.class)))
        .thenThrow(new DataIntegrityViolationException("uk_metric_client_name"));

    assertThatThrownBy(() -> service.register("client-a",
        request("latency", DetectionMethod.EWMA, null, null)))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(statisticsRepository);
  }

  @Test
  void registerRejectsInvalidRuleBeforeTouchingTheDatabase() {
    assertThatThrownBy(() -> service.register("client-a",
        request("latency", DetectionMethod.THRESHOLD, Map.of(), null)))
        .isInstanceOf(BadRequestException.class);
    verifyNoInteractions(metricRepository, statisticsRepository);
  }

  @Test
  void registerRejectsMissingClientId() {
    assertThatThrownBy(() -> service.register(null,
        request("latency", DetectionMethod.EWMA, null, null)))
        .isInstanceOf(UnauthorizedException.class);
    verifyNoInteractions(statisticsRepository);
  }

  @Test
  void registerKeepsExplicitParamsAndWarmUp() {
    when(metricRepository.saveAndFlush(any(Metric.class))).thenAnswer(call -> call.getArgument(0));

    Metric saved = service.register("client-a", request("temp", DetectionMethod.THRESHOLD,
        Map.of(RuleParams.LOWER_BOUND, 0.0, RuleParams.UPPER_BOUND, 100.0), 3));

    assertThat(saved.getParams()).containsEntry(RuleParams.LOWER_BOUND, 0.0)
        .containsEntry(RuleParams.UPPER_BOUND, 100.0);
    assertThat(saved.getWarmUpSamples()).isEqualTo(3);
  }
}
