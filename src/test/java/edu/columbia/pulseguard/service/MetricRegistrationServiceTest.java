package edu.columbia.pulseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.api.ConflictException;
import edu.columbia.pulseguard.detection.RuleParams;
import edu.columbia.pulseguard.dto.RegisterMetricRequest;
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

// AI-generated: Claude (Anthropic)
/**
 * Unit tests for {@link MetricRegistrationService}, with repositories mocked.
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
    service = new MetricRegistrationService(new MetricLookupService(metricRepository),
        metricRepository, statisticsRepository);
  }

  @Test
  void registersMetricWithDefaultsAndCreatesStatistics() {
    when(metricRepository.existsByClientIdAndName("client-a", "cpu")).thenReturn(false);
    when(metricRepository.save(any(Metric.class))).thenAnswer(call -> call.getArgument(0));

    Metric metric = service.register(" client-a ",
        new RegisterMetricRequest(" cpu ", "%", DetectionMethod.ZSCORE, null, null));

    assertThat(metric.getClientId()).isEqualTo("client-a");
    assertThat(metric.getName()).isEqualTo("cpu");
    assertThat(metric.getParams()).containsEntry(RuleParams.WINDOW_SIZE, 30.0);
    assertThat(metric.getWarmUpSamples()).isEqualTo(RuleParams.DEFAULT_WARM_UP_SAMPLES);
    verify(statisticsRepository).save(any(MetricStatistics.class));
  }

  @Test
  void storesGivenWarmUpAndParams() {
    when(metricRepository.save(any(Metric.class))).thenAnswer(call -> call.getArgument(0));
    ArgumentCaptor<Metric> saved = ArgumentCaptor.forClass(Metric.class);

    service.register("c", new RegisterMetricRequest("temp", null, DetectionMethod.THRESHOLD,
        Map.of(RuleParams.LOWER_BOUND, 5.0), 2));

    verify(metricRepository).save(saved.capture());
    assertThat(saved.getValue().getParams()).containsEntry(RuleParams.LOWER_BOUND, 5.0);
    assertThat(saved.getValue().getWarmUpSamples()).isEqualTo(2);
  }

  @Test
  void duplicateNameIsConflict() {
    when(metricRepository.existsByClientIdAndName("c", "cpu")).thenReturn(true);

    assertThatThrownBy(() -> service.register("c",
        new RegisterMetricRequest("cpu", null, DetectionMethod.EWMA, null, null)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("already exists");
    verify(metricRepository, never()).save(any());
  }

  @Test
  void invalidRuleIsRejectedBeforeSaving() {
    assertThatThrownBy(() -> service.register("c",
        new RegisterMetricRequest("cpu", null, DetectionMethod.THRESHOLD, Map.of(), null)))
        .isInstanceOf(BadRequestException.class);
    verify(metricRepository, never()).save(any());
  }
}
