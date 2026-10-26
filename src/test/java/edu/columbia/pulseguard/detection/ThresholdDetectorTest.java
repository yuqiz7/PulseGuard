package edu.columbia.pulseguard.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests threshold detection, boundary conditions,
 * warm-up, immutability, and invalid configuration.
 */
class ThresholdDetectorTest {
  private final ThresholdDetector detector = new ThresholdDetector();

  private MetricRule rule(int warmUpSamples) {
    return new MetricRule(DetectionMethod.THRESHOLD,
        Map.of(RuleParams.LOWER_BOUND, 20.0, RuleParams.UPPER_BOUND, 80.0), warmUpSamples);
  }

  @Test
  void valuesInsideRangeAreNormal() {
    MetricRule rule = rule(0);
    DetectorState state = DetectorState.empty();
    for (double value : new double[]{21.0, 50.0, 79.0}) {
      DetectionResult result = detector.evaluate(rule, state, value);

      assertThat(result.anomaly()).isFalse();
      assertThat(result.score()).isZero();
      assertThat(result.expectedLow()).isEqualTo(20.0);
      assertThat(result.expectedHigh()).isEqualTo(80.0);
    }
  }

  @Test
  void valuesOutsideRangeAreAnomalies() {
    MetricRule rule = rule(0);
    DetectorState state = DetectorState.empty();
    DetectionResult below = detector.evaluate(rule, state, 10.0);
    DetectionResult above = detector.evaluate(rule, state, 95.0);

    assertThat(below.anomaly()).isTrue();
    assertThat(below.score()).isEqualTo(10.0);
    assertThat(above.anomaly()).isTrue();
    assertThat(above.score()).isEqualTo(15.0);
  }

  @Test
  void exactBoundariesAreNormal() {
    MetricRule rule = rule(0);
    DetectorState state = DetectorState.empty();
    DetectionResult lower = detector.evaluate(rule, state, 20.0);
    DetectionResult upper = detector.evaluate(rule, state, 80.0);

    assertThat(lower.anomaly()).isFalse();
    assertThat(upper.anomaly()).isFalse();
    assertThat(lower.score()).isZero();
    assertThat(upper.score()).isZero();
  }

  @Test
  void warmUpSuppressesAnomalies() {
    MetricRule rule = rule(3);
    DetectorState state = DetectorState.empty();
    for (int i = 0; i < 3; i++) {
      DetectionResult result = detector.evaluate(rule, state, 100.0);

      assertThat(result.anomaly()).isFalse();
      assertThat(result.score()).isEqualTo(20.0);

      state = detector.update(rule, state, 100.0);
    }

    assertThat(state.sampleCount()).isEqualTo(3);
    DetectionResult result = detector.evaluate(rule, state, 100.0);
    assertThat(result.anomaly()).isTrue();
  }

  @Test
  void updateIncrementsCountAndPreservesOtherState() {
    MetricRule rule = rule(0);
    DetectorState original = new DetectorState(5, 42.0, 4.0, List.of(40.0, 42.0));
    DetectorState updated = detector.update(rule, original, 90.0);

    assertThat(updated.sampleCount()).isEqualTo(6);
    assertThat(updated.ewma()).isEqualTo(42.0);
    assertThat(updated.ewmaVariance()).isEqualTo(4.0);
    assertThat(updated.window()).containsExactly(40.0, 42.0);
    assertThat(original.sampleCount()).isEqualTo(5);
  }

  @Test
  void evaluationDoesNotModifyState() {
    MetricRule rule = rule(0);
    DetectorState state = DetectorState.empty();
    detector.evaluate(rule, state, 100.0);

    assertThat(state).isEqualTo(DetectorState.empty());
  }

  @Test
  void rejectsMissingBounds() {
    MetricRule rule = new MetricRule(DetectionMethod.THRESHOLD,
        Map.of(RuleParams.LOWER_BOUND, 20.0), 0);

    assertThatThrownBy(() -> detector.evaluate(rule, DetectorState.empty(), 50.0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsReversedBounds() {
    MetricRule rule = new MetricRule(DetectionMethod.THRESHOLD,
        Map.of(RuleParams.LOWER_BOUND, 80.0, RuleParams.UPPER_BOUND, 20.0), 0);

    assertThatThrownBy(() -> detector.evaluate(rule, DetectorState.empty(), 50.0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonFiniteValues() {
    MetricRule rule = rule(0);

    assertThatThrownBy(() -> detector.evaluate(rule, DetectorState.empty(),
        Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> detector.update(rule, DetectorState.empty(),
        Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsWrongDetectionMethod() {
    MetricRule rule = new MetricRule(DetectionMethod.ZSCORE, Map.of(), 0);

    assertThatThrownBy(() -> detector.evaluate(rule, DetectorState.empty(), 50.0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
