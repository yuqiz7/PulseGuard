package edu.columbia.pulseguard.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

// AI-generated: Claude (Anthropic)
/**
 * Unit tests for {@link RuleValidator}.
 */
class RuleValidatorTest {

  @Test
  void zscoreDefaultsAreApplied() {
    Map<String, Double> params = RuleValidator.normalizeParams(DetectionMethod.ZSCORE, null);

    assertThat(params)
        .containsEntry(RuleParams.WINDOW_SIZE, RuleParams.DEFAULT_WINDOW_SIZE)
        .containsEntry(RuleParams.Z_THRESHOLD, RuleParams.DEFAULT_Z_THRESHOLD);
  }

  @Test
  void ewmaKeepsGivenValuesAndFillsMissingOnes() {
    Map<String, Double> params =
        RuleValidator.normalizeParams(DetectionMethod.EWMA, Map.of(RuleParams.ALPHA, 0.5));

    assertThat(params)
        .containsEntry(RuleParams.ALPHA, 0.5)
        .containsEntry(RuleParams.TOLERANCE, RuleParams.DEFAULT_TOLERANCE);
  }

  @Test
  void thresholdAcceptsSingleBound() {
    Map<String, Double> params = RuleValidator.normalizeParams(DetectionMethod.THRESHOLD,
        Map.of(RuleParams.UPPER_BOUND, 80.0));

    assertThat(params).containsOnlyKeys(RuleParams.UPPER_BOUND);
  }

  @Test
  void rejectsUnknownKeyForMethod() {
    assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.ZSCORE,
        Map.of(RuleParams.ALPHA, 0.5)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Unknown parameter 'alpha'");
  }

  @Test
  void rejectsNullOrNonFiniteValues() {
    Map<String, Double> withNull = new HashMap<>();
    withNull.put(RuleParams.ALPHA, null);

    assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.EWMA, withNull))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.EWMA,
        Map.of(RuleParams.ALPHA, Double.NaN)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void rejectsInvalidWindowSizes() {
    for (double windowSize : new double[] {1.0, 2.5, 10_001.0}) {
      assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.ZSCORE,
          Map.of(RuleParams.WINDOW_SIZE, windowSize)))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("windowSize");
    }
  }

  @Test
  void acceptsWindowSizeBoundaries() {
    assertThat(RuleValidator.normalizeParams(DetectionMethod.ZSCORE,
        Map.of(RuleParams.WINDOW_SIZE, 2.0))).containsEntry(RuleParams.WINDOW_SIZE, 2.0);
    assertThat(RuleValidator.normalizeParams(DetectionMethod.ZSCORE,
        Map.of(RuleParams.WINDOW_SIZE, 10_000.0)))
        .containsEntry(RuleParams.WINDOW_SIZE, 10_000.0);
  }

  @Test
  void rejectsNonPositiveZthreshold() {
    assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.ZSCORE,
        Map.of(RuleParams.Z_THRESHOLD, 0.0)))
        .hasMessageContaining("zThreshold");
  }

  @Test
  void rejectsAlphaOutsideOpenInterval() {
    assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.EWMA,
        Map.of(RuleParams.ALPHA, 0.0))).hasMessageContaining("alpha");
    assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.EWMA,
        Map.of(RuleParams.ALPHA, 1.0))).hasMessageContaining("alpha");
  }

  @Test
  void rejectsNonPositiveTolerance() {
    assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.EWMA,
        Map.of(RuleParams.TOLERANCE, -1.0))).hasMessageContaining("tolerance");
  }

  @Test
  void thresholdRequiresAtLeastOneBound() {
    assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.THRESHOLD, Map.of()))
        .hasMessageContaining("lowerBound and/or upperBound");
  }

  @Test
  void thresholdRejectsInvertedBounds() {
    assertThatThrownBy(() -> RuleValidator.normalizeParams(DetectionMethod.THRESHOLD,
        Map.of(RuleParams.LOWER_BOUND, 10.0, RuleParams.UPPER_BOUND, 10.0)))
        .hasMessageContaining("lowerBound must be less than upperBound");
  }

  @Test
  void warmUpDefaultsDependOnMethod() {
    assertThat(RuleValidator.normalizeWarmUp(DetectionMethod.THRESHOLD, null)).isZero();
    assertThat(RuleValidator.normalizeWarmUp(DetectionMethod.ZSCORE, null))
        .isEqualTo(RuleParams.DEFAULT_WARM_UP_SAMPLES);
    assertThat(RuleValidator.normalizeWarmUp(DetectionMethod.EWMA, 0)).isZero();
  }

  @Test
  void warmUpOutOfRangeIsRejected() {
    assertThatThrownBy(() -> RuleValidator.normalizeWarmUp(DetectionMethod.EWMA, -1))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> RuleValidator.normalizeWarmUp(DetectionMethod.EWMA, 10_001))
        .isInstanceOf(BadRequestException.class);
  }
}
