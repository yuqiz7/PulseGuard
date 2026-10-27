package edu.columbia.pulseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.detection.RuleParams;
import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Unit tests for {@link MetricRuleValidator}.
 */
class MetricRuleValidatorTest {

  private static Map<String, Double> params(Object... keysAndValues) {
    Map<String, Double> map = new HashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], (Double) keysAndValues[i + 1]);
    }
    return map;
  }

  @Test
  void zscoreDefaultsAreFilledIn() {
    Map<String, Double> result = MetricRuleValidator.normalizeParams(DetectionMethod.ZSCORE, null);

    assertThat(result).containsEntry(RuleParams.WINDOW_SIZE, RuleParams.DEFAULT_WINDOW_SIZE)
        .containsEntry(RuleParams.Z_THRESHOLD, RuleParams.DEFAULT_Z_THRESHOLD);
  }

  @Test
  void zscoreKeepsClientValues() {
    Map<String, Double> result = MetricRuleValidator.normalizeParams(DetectionMethod.ZSCORE,
        params(RuleParams.WINDOW_SIZE, 5.0, RuleParams.Z_THRESHOLD, 2.5));

    assertThat(result).containsEntry(RuleParams.WINDOW_SIZE, 5.0)
        .containsEntry(RuleParams.Z_THRESHOLD, 2.5);
  }

  @Test
  void zscoreRejectsInvalidWindowSizes() {
    for (double size : new double[] {1.0, 2.5, -3.0, MetricRuleValidator.MAX_WINDOW_SIZE + 1}) {
      assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.ZSCORE,
          params(RuleParams.WINDOW_SIZE, size)))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("windowSize");
    }
  }

  @Test
  void zscoreAcceptsWindowSizeBoundaries() {
    assertThat(MetricRuleValidator.normalizeParams(DetectionMethod.ZSCORE,
        params(RuleParams.WINDOW_SIZE, 2.0))).containsEntry(RuleParams.WINDOW_SIZE, 2.0);
    assertThat(MetricRuleValidator.normalizeParams(DetectionMethod.ZSCORE,
        params(RuleParams.WINDOW_SIZE, (double) MetricRuleValidator.MAX_WINDOW_SIZE)))
        .containsKey(RuleParams.WINDOW_SIZE);
  }

  @Test
  void zscoreRejectsNonPositiveThreshold() {
    assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.ZSCORE,
        params(RuleParams.Z_THRESHOLD, 0.0)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("zThreshold");
  }

  @Test
  void ewmaDefaultsAreFilledIn() {
    Map<String, Double> result =
        MetricRuleValidator.normalizeParams(DetectionMethod.EWMA, Map.of());

    assertThat(result).containsEntry(RuleParams.ALPHA, RuleParams.DEFAULT_ALPHA)
        .containsEntry(RuleParams.TOLERANCE, RuleParams.DEFAULT_TOLERANCE);
  }

  @Test
  void ewmaRejectsAlphaOutsideOpenInterval() {
    for (double alpha : new double[] {0.0, 1.0, -0.1, 1.5}) {
      assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.EWMA,
          params(RuleParams.ALPHA, alpha)))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("alpha");
    }
  }

  @Test
  void ewmaRejectsNonPositiveTolerance() {
    assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.EWMA,
        params(RuleParams.TOLERANCE, -1.0)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("tolerance");
  }

  @Test
  void thresholdAcceptsValidBounds() {
    Map<String, Double> result = MetricRuleValidator.normalizeParams(DetectionMethod.THRESHOLD,
        params(RuleParams.LOWER_BOUND, -1.0, RuleParams.UPPER_BOUND, 10.0));

    assertThat(result).containsEntry(RuleParams.LOWER_BOUND, -1.0)
        .containsEntry(RuleParams.UPPER_BOUND, 10.0);
  }

  @Test
  void thresholdRequiresBothBounds() {
    assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.THRESHOLD, null))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.THRESHOLD,
        params(RuleParams.LOWER_BOUND, 1.0)))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.THRESHOLD,
        params(RuleParams.UPPER_BOUND, 1.0)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void thresholdRejectsEqualOrInvertedBounds() {
    for (double upper : new double[] {5.0, 4.0}) {
      assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.THRESHOLD,
          params(RuleParams.LOWER_BOUND, 5.0, RuleParams.UPPER_BOUND, upper)))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("lowerBound");
    }
  }

  @Test
  void unknownKeyForMethodIsRejected() {
    assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.EWMA,
        params(RuleParams.WINDOW_SIZE, 5.0)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Unknown parameter 'windowSize'");
  }

  @Test
  void nonFiniteAndNullValuesAreRejected() {
    assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.EWMA,
        params(RuleParams.ALPHA, Double.NaN)))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.EWMA,
        params(RuleParams.ALPHA, null)))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> MetricRuleValidator.normalizeParams(DetectionMethod.THRESHOLD,
        params(RuleParams.LOWER_BOUND, 0.0, RuleParams.UPPER_BOUND, Double.POSITIVE_INFINITY)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void warmUpDefaultsDependOnMethod() {
    assertThat(MetricRuleValidator.normalizeWarmUp(DetectionMethod.THRESHOLD, null)).isZero();
    assertThat(MetricRuleValidator.normalizeWarmUp(DetectionMethod.ZSCORE, null))
        .isEqualTo(RuleParams.DEFAULT_WARM_UP_SAMPLES);
    assertThat(MetricRuleValidator.normalizeWarmUp(DetectionMethod.EWMA, null))
        .isEqualTo(RuleParams.DEFAULT_WARM_UP_SAMPLES);
  }

  @Test
  void warmUpBoundariesAreInclusive() {
    assertThat(MetricRuleValidator.normalizeWarmUp(DetectionMethod.EWMA, 0)).isZero();
    assertThat(MetricRuleValidator.normalizeWarmUp(DetectionMethod.EWMA,
        MetricRuleValidator.MAX_WARM_UP_SAMPLES))
        .isEqualTo(MetricRuleValidator.MAX_WARM_UP_SAMPLES);
  }

  @Test
  void warmUpOutOfRangeIsRejected() {
    assertThatThrownBy(() -> MetricRuleValidator.normalizeWarmUp(DetectionMethod.EWMA, -1))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> MetricRuleValidator.normalizeWarmUp(DetectionMethod.EWMA,
        MetricRuleValidator.MAX_WARM_UP_SAMPLES + 1))
        .isInstanceOf(BadRequestException.class);
  }
}
