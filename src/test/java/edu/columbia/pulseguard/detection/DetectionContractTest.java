package edu.columbia.pulseguard.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// AI-generated: Claude (Anthropic)
/**
 * Unit tests for the immutable detection contract types.
 */
class DetectionContractTest {

  @Test
  void ruleFromMetricCopiesConfiguration() {
    Metric metric = new Metric("c", "latency", "ms", DetectionMethod.ZSCORE,
        Map.of(RuleParams.WINDOW_SIZE, 15.0), 4);
    MetricRule rule = MetricRule.fromMetric(metric);

    assertThat(rule.method()).isEqualTo(DetectionMethod.ZSCORE);
    assertThat(rule.warmUpSamples()).isEqualTo(4);
    assertThat(rule.param(RuleParams.WINDOW_SIZE, RuleParams.DEFAULT_WINDOW_SIZE)).isEqualTo(15.0);
  }

  @Test
  void ruleParamFallsBackToDefault() {
    MetricRule rule = new MetricRule(DetectionMethod.EWMA, Map.of(), 0);
    assertThat(rule.param(RuleParams.ALPHA, RuleParams.DEFAULT_ALPHA))
        .isEqualTo(RuleParams.DEFAULT_ALPHA);
  }

  @Test
  void ruleParamsCannotBeModifiedThroughSource() {
    Map<String, Double> params = new HashMap<>(Map.of(RuleParams.UPPER_BOUND, 10.0));
    MetricRule rule = new MetricRule(DetectionMethod.THRESHOLD, params, 0);
    params.put(RuleParams.UPPER_BOUND, 99.0);

    assertThat(rule.params()).containsEntry(RuleParams.UPPER_BOUND, 10.0);
    assertThatThrownBy(() -> rule.params().put("x", 1.0))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void ruleRejectsMissingMethodOrParams() {
    assertThatThrownBy(() -> new MetricRule(null, Map.of(), 0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new MetricRule(DetectionMethod.EWMA, null, 0))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void emptyStateHasNoHistory() {
    DetectorState state = DetectorState.empty();

    assertThat(state.sampleCount()).isZero();
    assertThat(state.ewma()).isNull();
    assertThat(state.ewmaVariance()).isNull();
    assertThat(state.window()).isEmpty();
  }

  @Test
  void stateWindowIsCopied() {
    List<Double> window = new ArrayList<>(List.of(1.0, 2.0));
    DetectorState state = new DetectorState(2, null, null, window);
    window.add(3.0);

    assertThat(state.window()).containsExactly(1.0, 2.0);
  }

  @Test
  void resultExposesVerdict() {
    DetectionResult result = new DetectionResult(true, 4.5, 1.0, 9.0);

    assertThat(result.anomaly()).isTrue();
    assertThat(result.score()).isEqualTo(4.5);
    assertThat(result.expectedLow()).isEqualTo(1.0);
    assertThat(result.expectedHigh()).isEqualTo(9.0);
  }
}
