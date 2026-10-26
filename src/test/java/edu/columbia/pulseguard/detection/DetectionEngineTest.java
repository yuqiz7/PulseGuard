package edu.columbia.pulseguard.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// AI-generated: Claude (Anthropic)
/**
 * Unit tests for {@link DetectionEngine}: one nested group per detection method plus the
 * shared warm-up and state-update behavior.
 */
class DetectionEngineTest {

  private final DetectionEngine engine = new DetectionEngine();

  private static DetectorState stateWithWindow(List<Double> window) {
    return new DetectorState(window.size(), null, null, window);
  }

  private DetectorState feed(MetricRule rule, double... values) {
    DetectorState state = DetectorState.empty();
    for (double value : values) {
      state = engine.update(rule, state, value);
    }
    return state;
  }

  /** Warm-up behavior shared by all methods. */
  @Nested
  class WarmUp {

    @Test
    void neverFlagsDuringWarmUp() {
      MetricRule rule = new MetricRule(DetectionMethod.THRESHOLD,
          Map.of(RuleParams.UPPER_BOUND, 10.0), 3);
      DetectorState state = new DetectorState(2, null, null, List.of());

      DetectionResult result = engine.evaluate(rule, state, 1000.0);

      assertThat(result.anomaly()).isFalse();
      assertThat(result.score()).isZero();
    }

    @Test
    void flagsOnceWarmUpIsReached() {
      MetricRule rule = new MetricRule(DetectionMethod.THRESHOLD,
          Map.of(RuleParams.UPPER_BOUND, 10.0), 3);
      DetectorState state = new DetectorState(3, null, null, List.of());

      assertThat(engine.evaluate(rule, state, 1000.0).anomaly()).isTrue();
    }
  }

  /** Rolling-window z-score detection. */
  @Nested
  class Zscore {

    private final MetricRule rule = new MetricRule(DetectionMethod.ZSCORE,
        Map.of(RuleParams.WINDOW_SIZE, 5.0, RuleParams.Z_THRESHOLD, 2.0), 0);

    @Test
    void valueNearMeanIsNormal() {
      DetectorState state = stateWithWindow(List.of(10.0, 12.0, 10.0, 12.0));

      DetectionResult result = engine.evaluate(rule, state, 11.0);

      assertThat(result.anomaly()).isFalse();
      assertThat(result.score()).isCloseTo(0.0, within(1e-9));
      assertThat(result.expectedLow()).isCloseTo(9.0, within(1e-9));
      assertThat(result.expectedHigh()).isCloseTo(13.0, within(1e-9));
    }

    @Test
    void valueFarFromMeanIsAnomalous() {
      DetectorState state = stateWithWindow(List.of(10.0, 12.0, 10.0, 12.0));

      DetectionResult result = engine.evaluate(rule, state, 20.0);

      assertThat(result.anomaly()).isTrue();
      assertThat(result.score()).isCloseTo(9.0, within(1e-9));
    }

    @Test
    void valueExactlyAtThresholdIsNotAnomalous() {
      DetectorState state = stateWithWindow(List.of(10.0, 12.0, 10.0, 12.0));

      DetectionResult result = engine.evaluate(rule, state, 13.0);

      assertThat(result.score()).isCloseTo(2.0, within(1e-9));
      assertThat(result.anomaly()).isFalse();
    }

    @Test
    void lowSideDeviationIsAnomalous() {
      DetectorState state = stateWithWindow(List.of(10.0, 12.0, 10.0, 12.0));

      assertThat(engine.evaluate(rule, state, 2.0).anomaly()).isTrue();
    }

    @Test
    void zeroVarianceSeriesFlagsAnyChange() {
      DetectorState state = stateWithWindow(List.of(5.0, 5.0, 5.0));

      assertThat(engine.evaluate(rule, state, 5.0).anomaly()).isFalse();
      assertThat(engine.evaluate(rule, state, 5.1).anomaly()).isTrue();
    }

    @Test
    void fewerThanTwoValuesIsNeverAnomalous() {
      assertThat(engine.evaluate(rule, stateWithWindow(List.of()), 99.0).anomaly()).isFalse();
      assertThat(engine.evaluate(rule, stateWithWindow(List.of(1.0)), 99.0).anomaly()).isFalse();
    }

    @Test
    void usesDefaultThresholdWhenNotConfigured() {
      MetricRule defaults = new MetricRule(DetectionMethod.ZSCORE, Map.of(), 0);
      DetectorState state = stateWithWindow(List.of(10.0, 12.0, 10.0, 12.0));

      assertThat(engine.evaluate(defaults, state, 13.5).anomaly()).isFalse();
      assertThat(engine.evaluate(defaults, state, 14.5).anomaly()).isTrue();
    }

    @Test
    void outlierCannotHideAfterWindowFills() {
      DetectorState state = feed(rule, 10.0, 11.0, 10.0, 11.0, 10.0);

      assertThat(engine.evaluate(rule, state, 50.0).anomaly()).isTrue();
    }
  }

  /** Exponentially weighted moving average detection. */
  @Nested
  class Ewma {

    private final MetricRule rule = new MetricRule(DetectionMethod.EWMA,
        Map.of(RuleParams.ALPHA, 0.5, RuleParams.TOLERANCE, 2.0), 0);

    @Test
    void firstValueIsNeverAnomalous() {
      assertThat(engine.evaluate(rule, DetectorState.empty(), 100.0).anomaly()).isFalse();
    }

    @Test
    void stableSeriesIsNormalAndSpikeIsAnomalous() {
      DetectorState state = feed(rule, 10.0, 12.0, 10.0, 12.0, 10.0, 12.0);

      assertThat(engine.evaluate(rule, state, 11.0).anomaly()).isFalse();
      assertThat(engine.evaluate(rule, state, 40.0).anomaly()).isTrue();
    }

    @Test
    void zeroVarianceFlagsAnyChange() {
      DetectorState state = feed(rule, 7.0, 7.0, 7.0);

      assertThat(engine.evaluate(rule, state, 7.0).anomaly()).isFalse();
      assertThat(engine.evaluate(rule, state, 7.5).anomaly()).isTrue();
    }

    @Test
    void expectedRangeIsCenteredOnEwma() {
      DetectorState state = new DetectorState(4, 10.0, 4.0, List.of());

      DetectionResult result = engine.evaluate(rule, state, 13.0);

      assertThat(result.expectedLow()).isCloseTo(6.0, within(1e-9));
      assertThat(result.expectedHigh()).isCloseTo(14.0, within(1e-9));
      assertThat(result.score()).isCloseTo(1.5, within(1e-9));
      assertThat(result.anomaly()).isFalse();
    }

    @Test
    void missingVarianceIsTreatedAsZero() {
      DetectorState state = new DetectorState(1, 10.0, null, List.of());

      assertThat(engine.evaluate(rule, state, 11.0).anomaly()).isTrue();
    }
  }

  /** Static bounds detection. */
  @Nested
  class Threshold {

    private final MetricRule rule = new MetricRule(DetectionMethod.THRESHOLD,
        Map.of(RuleParams.LOWER_BOUND, 0.0, RuleParams.UPPER_BOUND, 10.0), 0);

    @Test
    void valueInsideBoundsIsNormal() {
      DetectionResult result = engine.evaluate(rule, DetectorState.empty(), 5.0);

      assertThat(result.anomaly()).isFalse();
      assertThat(result.score()).isZero();
      assertThat(result.expectedLow()).isEqualTo(0.0);
      assertThat(result.expectedHigh()).isEqualTo(10.0);
    }

    @Test
    void boundariesAreInclusive() {
      assertThat(engine.evaluate(rule, DetectorState.empty(), 0.0).anomaly()).isFalse();
      assertThat(engine.evaluate(rule, DetectorState.empty(), 10.0).anomaly()).isFalse();
    }

    @Test
    void valuesOutsideBoundsScoreTheirDistance() {
      DetectionResult above = engine.evaluate(rule, DetectorState.empty(), 12.5);
      DetectionResult below = engine.evaluate(rule, DetectorState.empty(), -3.0);

      assertThat(above.anomaly()).isTrue();
      assertThat(above.score()).isEqualTo(2.5);
      assertThat(below.anomaly()).isTrue();
      assertThat(below.score()).isEqualTo(3.0);
    }

    @Test
    void missingBoundIsUnbounded() {
      MetricRule upperOnly = new MetricRule(DetectionMethod.THRESHOLD,
          Map.of(RuleParams.UPPER_BOUND, 10.0), 0);

      assertThat(engine.evaluate(upperOnly, DetectorState.empty(), -1e12).anomaly()).isFalse();
      assertThat(engine.evaluate(upperOnly, DetectorState.empty(), 11.0).anomaly()).isTrue();
    }
  }

  /** State updates used by every method. */
  @Nested
  class Update {

    private final MetricRule rule = new MetricRule(DetectionMethod.ZSCORE,
        Map.of(RuleParams.WINDOW_SIZE, 3.0, RuleParams.ALPHA, 0.5), 0);

    @Test
    void firstValueInitializesEwma() {
      DetectorState state = engine.update(rule, DetectorState.empty(), 8.0);

      assertThat(state.sampleCount()).isEqualTo(1);
      assertThat(state.ewma()).isEqualTo(8.0);
      assertThat(state.ewmaVariance()).isEqualTo(0.0);
      assertThat(state.window()).containsExactly(8.0);
    }

    @Test
    void ewmaAndVarianceFollowRecurrence() {
      DetectorState state = feed(rule, 10.0, 20.0);

      assertThat(state.ewma()).isCloseTo(15.0, within(1e-9));
      assertThat(state.ewmaVariance()).isCloseTo(25.0, within(1e-9));
    }

    @Test
    void windowKeepsMostRecentValues() {
      DetectorState state = feed(rule, 1.0, 2.0, 3.0, 4.0, 5.0);

      assertThat(state.window()).containsExactly(3.0, 4.0, 5.0);
      assertThat(state.sampleCount()).isEqualTo(5);
    }

    @Test
    void windowSizeFallsBackToDefault() {
      MetricRule defaults = new MetricRule(DetectionMethod.EWMA, Map.of(), 0);

      assertThat(DetectionEngine.windowSize(defaults))
          .isEqualTo((int) RuleParams.DEFAULT_WINDOW_SIZE);
    }

    @Test
    void updateDoesNotModifyPreviousState() {
      DetectorState before = feed(rule, 1.0);
      engine.update(rule, before, 2.0);

      assertThat(before.window()).containsExactly(1.0);
      assertThat(before.sampleCount()).isEqualTo(1);
    }
  }
}
