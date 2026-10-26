
package edu.columbia.pulseguard.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests rolling Z-score detection, window management, warm-up,
 * zero variance, boundary values, and invalid parameters.
 */
class ZScoreDetectorTest {
    private final ZScoreDetector detector = new ZScoreDetector();
    private MetricRule rule(int windowSize, double threshold, int warmUp) {
        return new MetricRule(
                DetectionMethod.ZSCORE,
                Map.of(
                        RuleParams.WINDOW_SIZE, (double) windowSize,
                        RuleParams.Z_THRESHOLD, threshold
                ),
                warmUp
        );
    }

    @Test
    void detectsOutlierUsingCorrectZScore() {
        MetricRule rule = rule(3, 2.0, 0);
        DetectorState state = new DetectorState(3, null, null, List.of(8.0, 10.0, 12.0));
        DetectionResult result = detector.evaluate(rule, state, 14.0);
        // mean = 10, population stddev = sqrt(8/3)
        double stddev = Math.sqrt(8.0 / 3.0);

        assertThat(result.anomaly()).isTrue();
        assertThat(result.score()).isCloseTo(4.0 / stddev, within(1e-9));
        assertThat(result.expectedLow()).isCloseTo(10.0 - 2.0 * stddev, within(1e-9));
        assertThat(result.expectedHigh()).isCloseTo(10.0 + 2.0 * stddev, within(1e-9));
    }

    @Test
    void normalValueIsNotFlagged() {
        MetricRule rule = rule(3, 2.0, 0);
        DetectorState state = new DetectorState(3, null, null, List.of(8.0, 10.0, 12.0));
        DetectionResult result = detector.evaluate(rule, state, 11.0);

        assertThat(result.anomaly()).isFalse();
        assertThat(result.score()).isLessThan(2.0);
    }

    @Test
    void exactZScoreBoundaryIsNormal() {
        MetricRule rule = rule(2, 2.0, 0);
        DetectorState state = new DetectorState(2, null, null, List.of(0.0, 2.0));
        // mean = 1, stddev = 1, upper boundary = 3
        DetectionResult boundary = detector.evaluate(rule, state, 3.0);
        DetectionResult outside = detector.evaluate(rule, state, 3.1);

        assertThat(boundary.score()).isEqualTo(2.0);
        assertThat(boundary.anomaly()).isFalse();
        assertThat(outside.score()).isGreaterThan(2.0);
        assertThat(outside.anomaly()).isTrue();
    }

    @Test
    void zeroVarianceHandlesDifferentValues() {
        MetricRule rule = rule(3, 3.0, 0);
        DetectorState state = new DetectorState(3, null, null, List.of(10.0, 10.0, 10.0));
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
    void insufficientHistoryIsNotAnomalous() {
        MetricRule rule = rule(3, 2.0, 0);
        DetectionResult empty = detector.evaluate(rule, DetectorState.empty(), 100.0);
        DetectorState onePoint = new DetectorState(1, null, null, List.of(10.0));
        DetectionResult single = detector.evaluate(rule, onePoint, 100.0);

        assertThat(empty.anomaly()).isFalse();
        assertThat(single.anomaly()).isFalse();
        assertThat(single.score()).isZero();
    }

    @Test
    void warmUpSuppressesOutliers() {
        MetricRule rule = rule(3, 2.0, 3);
        DetectorState state = DetectorState.empty();
        for (double value : new double[] {10.0, 10.0, 30.0}) {
            DetectionResult result = detector.evaluate(rule, state, value);
            assertThat(result.anomaly()).isFalse();
            state = detector.update(rule, state, value);
        }

        assertThat(state.sampleCount()).isEqualTo(3);
        DetectionResult result = detector.evaluate(rule, state, 50.0);
        assertThat(result.anomaly()).isTrue();
    }

    @Test
    void updateMaintainsRollingWindow() {
        MetricRule rule = rule(3, 2.0, 0);
        DetectorState state = DetectorState.empty();
        for (double value : new double[] {10.0, 20.0, 30.0, 40.0}) {
            state = detector.update(rule, state, value);
        }

        assertThat(state.sampleCount()).isEqualTo(4);
        assertThat(state.window()).containsExactly(20.0, 30.0, 40.0);
    }

    @Test
    void updatePreservesOtherStatisticsAndOriginalState() {
        MetricRule rule = rule(3, 2.0, 0);
        DetectorState original = new DetectorState(3, 25.0, 4.0, List.of(10.0, 20.0, 30.0));
        DetectorState updated = detector.update(rule, original, 40.0);

        assertThat(updated.sampleCount()).isEqualTo(4);
        assertThat(updated.ewma()).isEqualTo(25.0);
        assertThat(updated.ewmaVariance()).isEqualTo(4.0);
        assertThat(updated.window()).containsExactly(20.0, 30.0, 40.0);
        assertThat(original.sampleCount()).isEqualTo(3);
        assertThat(original.window()).containsExactly(10.0, 20.0, 30.0);
    }

    @Test
    void evaluatesUsingOnlyMostRecentWindow() {
        MetricRule rule = rule(2, 2.0, 0);
        DetectorState state = new DetectorState(3, null, null, List.of(1000.0, 0.0, 2.0));
        DetectionResult result = detector.evaluate(rule, state, 3.0);

        assertThat(result.score()).isEqualTo(2.0);
        assertThat(result.anomaly()).isFalse();
        assertThat(result.expectedHigh()).isEqualTo(3.0);
    }

    @Test
    void evaluateDoesNotModifyState() {
        MetricRule rule = rule(3, 2.0, 0);
        DetectorState state = new DetectorState(3, null, null, List.of(8.0, 10.0, 12.0));
        detector.evaluate(rule, state, 100.0);

        assertThat(state.window()).containsExactly(8.0, 10.0, 12.0);
        assertThat(state.sampleCount()).isEqualTo(3);
    }

    @Test
    void defaultsAreSupported() {
        MetricRule rule = new MetricRule(DetectionMethod.ZSCORE, Map.of(), 0);
        DetectorState state = new DetectorState(2, null, null, List.of(0.0, 2.0));

        assertThat(detector.evaluate(rule, state, 4.0).anomaly()).isFalse();
        assertThat(detector.evaluate(rule, state, 5.0).anomaly()).isTrue();
    }

    @Test
    void rejectsInvalidWindowSizes() {
        for (double size : new double[] {1.0, 1.5, -1.0, Double.POSITIVE_INFINITY}) {
            MetricRule rule = new MetricRule(
                    DetectionMethod.ZSCORE,
                    Map.of(RuleParams.WINDOW_SIZE, size),
                    0
            );

            assertThatThrownBy(() -> detector.evaluate(
                    rule, DetectorState.empty(), 10.0
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsInvalidThreshold() {
        MetricRule rule = rule(3, 0.0, 0);

        assertThatThrownBy(() -> detector.evaluate(
                rule, DetectorState.empty(), 10.0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonFiniteMetricValues() {
        MetricRule rule = rule(3, 2.0, 0);

        assertThatThrownBy(() -> detector.evaluate(
                rule, DetectorState.empty(), Double.NaN
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> detector.update(
                rule, DetectorState.empty(), Double.POSITIVE_INFINITY
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWrongDetectionMethod() {
        MetricRule rule = new MetricRule(DetectionMethod.EWMA, Map.of(), 0);

        assertThatThrownBy(() -> detector.evaluate(
                rule, DetectorState.empty(), 10.0
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
