package edu.columbia.pulseguard.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests EWMA initialization, variance updates, anomaly detection,
 * warm-up, zero variance, boundaries, and invalid configuration.
 */
class EwmaDetectorTest {
  private final EwmaDetector detector = new EwmaDetector();

  private MetricRule rule(double alpha, double tolerance, int warmUp) {
    return new MetricRule(DetectionMethod.EWMA,
        Map.of(RuleParams.ALPHA, alpha, RuleParams.TOLERANCE, tolerance), warmUp);
  }

  @Test
  void firstValueInitializesState() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState empty = DetectorState.empty();
    DetectionResult result = detector.evaluate(rule, empty, 10.0);
    DetectorState updated = detector.update(rule, empty, 10.0);

    assertThat(result.anomaly()).isFalse();
    assertThat(result.score()).isZero();
    assertThat(result.expectedLow()).isEqualTo(10.0);
    assertThat(result.expectedHigh()).isEqualTo(10.0);
    assertThat(updated.sampleCount()).isEqualTo(1);
    assertThat(updated.ewma()).isEqualTo(10.0);
    assertThat(updated.ewmaVariance()).isZero();
  }

  @Test
  void updatesMeanAndVarianceCorrectly() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState state = DetectorState.empty();
    state = detector.update(rule, state, 10.0);
    state = detector.update(rule, state, 14.0);

    // mean = 12, variance = 4
    assertThat(state.ewma()).isEqualTo(12.0);
    assertThat(state.ewmaVariance()).isEqualTo(4.0);

    state = detector.update(rule, state, 16.0);

    // mean = 14, variance = 6
    assertThat(state.sampleCount()).isEqualTo(3);
    assertThat(state.ewma()).isEqualTo(14.0);
    assertThat(state.ewmaVariance()).isEqualTo(6.0);
  }

  @Test
  void detectsOutlierUsingCorrectScore() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState state = new DetectorState(3, 14.0, 6.0, List.of());
    DetectionResult result = detector.evaluate(rule, state, 20.0);
    double stddev = Math.sqrt(6.0);

    assertThat(result.anomaly()).isTrue();
    assertThat(result.score()).isCloseTo(6.0 / stddev, within(1e-9));
    assertThat(result.expectedLow()).isCloseTo(14.0 - 2.0 * stddev, within(1e-9));
    assertThat(result.expectedHigh()).isCloseTo(14.0 + 2.0 * stddev, within(1e-9));
  }

  @Test
  void normalValueIsNotFlagged() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState state = new DetectorState(3, 14.0, 6.0, List.of());
    DetectionResult result = detector.evaluate(rule, state, 16.0);

    assertThat(result.anomaly()).isFalse();
    assertThat(result.score()).isLessThan(2.0);
  }

  @Test
  void exactToleranceBoundaryIsNormal() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState state = new DetectorState(3, 10.0, 4.0, List.of());

    // stddev = 2, tolerance = 2
    // valid range is [6, 14]
    DetectionResult boundary = detector.evaluate(rule, state, 14.0);
    DetectionResult outside = detector.evaluate(rule, state, 14.1);

    assertThat(boundary.anomaly()).isFalse();
    assertThat(boundary.score()).isEqualTo(2.0);
    assertThat(boundary.expectedHigh()).isEqualTo(14.0);
    assertThat(outside.anomaly()).isTrue();
    assertThat(outside.score()).isGreaterThan(2.0);
  }

  @Test
  void zeroVarianceHandlesIdenticalValues() {
    MetricRule rule = rule(0.5, 3.0, 0);
    DetectorState state = new DetectorState(4, 10.0, 0.0, List.of());
    DetectionResult same = detector.evaluate(rule, state, 10.0);
    DetectionResult different = detector.evaluate(rule, state, 11.0);

    assertThat(same.anomaly()).isFalse();
    assertThat(same.score()).isZero();
    assertThat(different.anomaly()).isTrue();
    assertThat(different.score()).isEqualTo(Double.MAX_VALUE);
    assertThat(different.expectedLow()).isEqualTo(10.0);
    assertThat(different.expectedHigh()).isEqualTo(10.0);
  }

  @Test
  void warmUpSuppressesAnomalies() {
    MetricRule rule = rule(0.5, 2.0, 3);
    DetectorState state = DetectorState.empty();
    for (double value : new double[]{10.0, 10.0, 40.0}) {
      DetectionResult result = detector.evaluate(rule, state, value);

      assertThat(result.anomaly()).isFalse();
      state = detector.update(rule, state, value);
    }

    assertThat(state.sampleCount()).isEqualTo(3);

    // mean = 25, variance = 225
    // score for 70 = 3
    DetectionResult result = detector.evaluate(rule, state, 70.0);

    assertThat(result.anomaly()).isTrue();
    assertThat(result.score()).isEqualTo(3.0);
  }

  @Test
  void evaluationDoesNotModifyState() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState original = new DetectorState(3, 14.0, 6.0, List.of(10.0, 12.0));
    detector.evaluate(rule, original, 100.0);

    assertThat(original.sampleCount()).isEqualTo(3);
    assertThat(original.ewma()).isEqualTo(14.0);
    assertThat(original.ewmaVariance()).isEqualTo(6.0);
    assertThat(original.window()).containsExactly(10.0, 12.0);
  }

  @Test
  void updatePreservesWindowAndOriginalState() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState original = new DetectorState(2, 11.0, 1.0, List.of(10.0, 12.0));
    DetectorState updated = detector.update(rule, original, 14.0);

    assertThat(updated.sampleCount()).isEqualTo(3);
    assertThat(updated.ewma()).isEqualTo(12.5);
    assertThat(updated.ewmaVariance()).isEqualTo(2.75);
    assertThat(updated.window()).containsExactly(10.0, 12.0);
    assertThat(original.sampleCount()).isEqualTo(2);
    assertThat(original.ewma()).isEqualTo(11.0);
    assertThat(original.ewmaVariance()).isEqualTo(1.0);
  }

  @Test
  void defaultParametersAreSupported() {
    MetricRule rule = new MetricRule(DetectionMethod.EWMA, Map.of(), 0);
    DetectorState state = new DetectorState(3, 10.0, 1.0, List.of());

    assertThat(detector.evaluate(rule, state, 12.0).anomaly()).isFalse();
    assertThat(detector.evaluate(rule, state, 14.0).anomaly()).isTrue();

    DetectorState updated = detector.update(rule, state, 13.0);

    assertThat(updated.ewma()).isCloseTo(10.9, within(1e-9));
    assertThat(updated.ewmaVariance()).isCloseTo(2.59, within(1e-9));
  }

  @Test
  void rejectsInvalidAlpha() {
    for (double alpha : new double[]{
        0.0, 1.0, -0.5, Double.NaN, Double.POSITIVE_INFINITY
    }) {
      MetricRule rule = rule(alpha, 2.0, 0);

      assertThatThrownBy(() -> detector.evaluate(rule, DetectorState.empty(), 10.0))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsInvalidTolerance() {
    for (double tolerance : new double[]{
        0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY
    }) {
      MetricRule rule = rule(0.5, tolerance, 0);

      assertThatThrownBy(() -> detector.evaluate(rule, DetectorState.empty(), 10.0))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsIncompleteState() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState state = new DetectorState(3, null, null, List.of());

    assertThatThrownBy(() -> detector.evaluate(rule, state, 10.0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> detector.update(rule, state, 10.0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNegativeVariance() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState state = new DetectorState(3, 10.0, -1.0, List.of());

    assertThatThrownBy(() -> detector.evaluate(rule, state, 10.0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonFiniteValues() {
    MetricRule rule = rule(0.5, 2.0, 0);

    assertThatThrownBy(() -> detector.evaluate(rule, DetectorState.empty(),
        Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> detector.update(rule, DetectorState.empty(),
        Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsOverflowingStatistics() {
    MetricRule rule = rule(0.5, 2.0, 0);
    DetectorState state = new DetectorState(3, 0.0, 0.0, List.of());

    assertThatThrownBy(() -> detector.update(rule, state, Double.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsWrongDetectionMethod() {
    MetricRule rule = new MetricRule(DetectionMethod.ZSCORE, Map.of(), 0);

    assertThatThrownBy(() -> detector.evaluate(rule, DetectorState.empty(), 10.0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
