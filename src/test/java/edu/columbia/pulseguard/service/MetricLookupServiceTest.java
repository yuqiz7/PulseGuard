package edu.columbia.pulseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.columbia.pulseguard.api.NotFoundException;
import edu.columbia.pulseguard.api.UnauthorizedException;
import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.repository.MetricRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Unit tests for {@link MetricLookupService}, with the repository mocked.
 */
@ExtendWith(MockitoExtension.class)
class MetricLookupServiceTest {

  @Mock
  private MetricRepository metricRepository;

  private MetricLookupService service;

  @BeforeEach
  void setUp() {
    service = new MetricLookupService(metricRepository);
  }

  @Test
  void requireClientIdRejectsNullAndBlank() {
    assertThatThrownBy(() -> service.requireClientId(null))
        .isInstanceOf(UnauthorizedException.class);
    assertThatThrownBy(() -> service.requireClientId("   "))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("X-Client-Id");
  }

  @Test
  void requireClientIdTrimsValue() {
    assertThat(service.requireClientId("  client-a ")).isEqualTo("client-a");
  }

  @Test
  void requireOwnedMetricReturnsMetricOfCaller() {
    Metric metric = new Metric("client-a", "cpu", null, DetectionMethod.ZSCORE, Map.of(), 0);
    when(metricRepository.findByIdAndClientId(5L, "client-a")).thenReturn(Optional.of(metric));

    assertThat(service.requireOwnedMetric(" client-a", 5L)).isSameAs(metric);
    verify(metricRepository).findByIdAndClientId(5L, "client-a");
  }

  @Test
  void requireOwnedMetricReportsOtherClientsMetricAsNotFound() {
    when(metricRepository.findByIdAndClientId(5L, "client-b")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireOwnedMetric("client-b", 5L))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Metric 5 not found");
  }

  @Test
  void requireOwnedMetricRejectsMissingClientId() {
    assertThatThrownBy(() -> service.requireOwnedMetric("", 5L))
        .isInstanceOf(UnauthorizedException.class);
  }
}
