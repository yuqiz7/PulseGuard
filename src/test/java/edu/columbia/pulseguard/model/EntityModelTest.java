package edu.columbia.pulseguard.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.columbia.pulseguard.detection.DetectorState;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// AI-generated: Claude (Anthropic)
/**
 * Unit tests for the behavior of the entity classes (no database).
 */
class EntityModelTest {

  @Test
  void metricConstructorSetsFieldsAndCopiesParams() {
    Map<String, Double> params = new HashMap<>(Map.of("windowSize", 20.0));
    Metric metric = new Metric("client-a", "cpu", "%", DetectionMethod.ZSCORE, params, 5);
    params.put("windowSize", 99.0);

    assertThat(metric.getClientId()).isEqualTo("client-a");
    assertThat(metric.getName()).isEqualTo("cpu");
    assertThat(metric.getUnit()).isEqualTo("%");
    assertThat(metric.getMethod()).isEqualTo(DetectionMethod.ZSCORE);
    assertThat(metric.getParams()).containsEntry("windowSize", 20.0);
    assertThat(metric.getWarmUpSamples()).isEqualTo(5);
    assertThat(metric.getCreatedAt()).isNotNull();
    assertThat(metric.getId()).isNull();
  }

  @Test
  void metricParamsAreReadOnly() {
    Metric metric = new Metric("c", "m", null, DetectionMethod.EWMA, Map.of("alpha", 0.3), 0);
    assertThatThrownBy(() -> metric.getParams().put("alpha", 0.5))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void updateRuleReplacesMethodParamsAndWarmUp() {
    Metric metric = new Metric("c", "m", null, DetectionMethod.EWMA, Map.of("alpha", 0.3), 0);
    metric.updateRule(DetectionMethod.THRESHOLD, Map.of("upperBound", 10.0), 3);

    assertThat(metric.getMethod()).isEqualTo(DetectionMethod.THRESHOLD);
    assertThat(metric.getParams()).containsOnlyKeys("upperBound");
    assertThat(metric.getWarmUpSamples()).isEqualTo(3);
  }

  @Test
  void dataPointVerdictCanBeReplaced() {
    Instant ts = Instant.parse("2026-10-01T00:00:00Z");
    DataPoint point = new DataPoint(1L, ts, 42.0, false, 0.5);
    point.setVerdict(true, 4.2);

    assertThat(point.getMetricId()).isEqualTo(1L);
    assertThat(point.getTs()).isEqualTo(ts);
    assertThat(point.getValue()).isEqualTo(42.0);
    assertThat(point.isAnomaly()).isTrue();
    assertThat(point.getScore()).isEqualTo(4.2);
  }

  @Test
  void anomalyKeepsAllFields() {
    Instant ts = Instant.parse("2026-10-01T00:00:00Z");
    Anomaly anomaly = new Anomaly(2L, ts, 99.0, 5.1, 10.0, 20.0, DetectionMethod.ZSCORE);

    assertThat(anomaly.getMetricId()).isEqualTo(2L);
    assertThat(anomaly.getTs()).isEqualTo(ts);
    assertThat(anomaly.getValue()).isEqualTo(99.0);
    assertThat(anomaly.getScore()).isEqualTo(5.1);
    assertThat(anomaly.getExpectedLow()).isEqualTo(10.0);
    assertThat(anomaly.getExpectedHigh()).isEqualTo(20.0);
    assertThat(anomaly.getMethod()).isEqualTo(DetectionMethod.ZSCORE);
  }

  @Test
  void newStatisticsStartEmptyAndConvertToState() {
    MetricStatistics stats = new MetricStatistics(3L);
    DetectorState state = stats.toDetectorState(List.of(1.0, 2.0));

    assertThat(stats.getMetricId()).isEqualTo(3L);
    assertThat(stats.getLastTs()).isNull();
    assertThat(state.sampleCount()).isZero();
    assertThat(state.ewma()).isNull();
    assertThat(state.ewmaVariance()).isNull();
    assertThat(state.window()).containsExactly(1.0, 2.0);
  }

  @Test
  void applyStoresUpdatedState() {
    MetricStatistics stats = new MetricStatistics(3L);
    Instant ts = Instant.parse("2026-10-01T00:00:00Z");
    stats.apply(new DetectorState(7, 12.5, 2.25, List.of(12.0)), ts);

    assertThat(stats.getSampleCount()).isEqualTo(7);
    assertThat(stats.getEwma()).isEqualTo(12.5);
    assertThat(stats.getEwmaVariance()).isEqualTo(2.25);
    assertThat(stats.getLastTs()).isEqualTo(ts);
  }
}
