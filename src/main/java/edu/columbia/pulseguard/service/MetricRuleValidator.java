package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.detection.RuleParams;
import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Validates detection parameters per method and fills in defaults.
 *
 * <p>Used by registerMetric; any endpoint that accepts a rule can reuse it.
 */
public final class MetricRuleValidator {

  /** Largest accepted z-score window size. */
  public static final int MAX_WINDOW_SIZE = 10_000;
  /** Largest accepted warm-up sample count. */
  public static final int MAX_WARM_UP_SAMPLES = 10_000;

  private static final Map<DetectionMethod, Set<String>> ALLOWED_KEYS = Map.of(
      DetectionMethod.ZSCORE, Set.of(RuleParams.WINDOW_SIZE, RuleParams.Z_THRESHOLD),
      DetectionMethod.EWMA, Set.of(RuleParams.ALPHA, RuleParams.TOLERANCE),
      DetectionMethod.THRESHOLD, Set.of(RuleParams.LOWER_BOUND, RuleParams.UPPER_BOUND));

  private MetricRuleValidator() {
  }

  /**
   * Validates the parameters of a method and returns them with defaults applied.
   *
   * @param method the detection method
   * @param params the client's parameters; null means none
   * @return a new map holding every parameter the method uses
   * @throws BadRequestException if a key is unknown for the method or a value is invalid
   */
  public static Map<String, Double> normalizeParams(DetectionMethod method,
      Map<String, Double> params) {
    Map<String, Double> result = new HashMap<>();
    if (params != null) {
      Set<String> allowed = ALLOWED_KEYS.get(method);
      params.forEach((key, value) -> {
        if (!allowed.contains(key)) {
          throw new BadRequestException("Unknown parameter '" + key + "' for " + method
              + "; allowed: " + new TreeSet<>(allowed));
        }
        if (value == null || !Double.isFinite(value)) {
          throw new BadRequestException("Parameter '" + key + "' must be a finite number");
        }
        result.put(key, value);
      });
    }
    return switch (method) {
      case ZSCORE -> normalizeZscore(result);
      case EWMA -> normalizeEwma(result);
      case THRESHOLD -> normalizeThreshold(result);
    };
  }

  /**
   * Validates the warm-up sample count.
   *
   * @param method the detection method
   * @param warmUpSamples the client's value; null means use the default (0 for THRESHOLD,
   *     {@link RuleParams#DEFAULT_WARM_UP_SAMPLES} otherwise)
   * @return the warm-up sample count to store
   * @throws BadRequestException if the value is negative or too large
   */
  public static int normalizeWarmUp(DetectionMethod method, Integer warmUpSamples) {
    if (warmUpSamples == null) {
      return method == DetectionMethod.THRESHOLD ? 0 : RuleParams.DEFAULT_WARM_UP_SAMPLES;
    }
    if (warmUpSamples < 0 || warmUpSamples > MAX_WARM_UP_SAMPLES) {
      throw new BadRequestException(
          "warmUpSamples must be between 0 and " + MAX_WARM_UP_SAMPLES);
    }
    return warmUpSamples;
  }

  private static Map<String, Double> normalizeZscore(Map<String, Double> params) {
    double windowSize = params.getOrDefault(RuleParams.WINDOW_SIZE,
        RuleParams.DEFAULT_WINDOW_SIZE);
    if (windowSize != Math.rint(windowSize) || windowSize < 2 || windowSize > MAX_WINDOW_SIZE) {
      throw new BadRequestException(
          "windowSize must be a whole number between 2 and " + MAX_WINDOW_SIZE);
    }
    double threshold = params.getOrDefault(RuleParams.Z_THRESHOLD,
        RuleParams.DEFAULT_Z_THRESHOLD);
    if (threshold <= 0) {
      throw new BadRequestException("zThreshold must be greater than 0");
    }
    params.put(RuleParams.WINDOW_SIZE, windowSize);
    params.put(RuleParams.Z_THRESHOLD, threshold);
    return params;
  }

  private static Map<String, Double> normalizeEwma(Map<String, Double> params) {
    double alpha = params.getOrDefault(RuleParams.ALPHA, RuleParams.DEFAULT_ALPHA);
    if (alpha <= 0 || alpha >= 1) {
      throw new BadRequestException("alpha must be between 0 and 1 (exclusive)");
    }
    double tolerance = params.getOrDefault(RuleParams.TOLERANCE, RuleParams.DEFAULT_TOLERANCE);
    if (tolerance <= 0) {
      throw new BadRequestException("tolerance must be greater than 0");
    }
    params.put(RuleParams.ALPHA, alpha);
    params.put(RuleParams.TOLERANCE, tolerance);
    return params;
  }

  private static Map<String, Double> normalizeThreshold(Map<String, Double> params) {
    Double lower = params.get(RuleParams.LOWER_BOUND);
    Double upper = params.get(RuleParams.UPPER_BOUND);
    if (lower == null || upper == null) {
      throw new BadRequestException("THRESHOLD requires both lowerBound and upperBound");
    }
    if (lower >= upper) {
      throw new BadRequestException("lowerBound must be less than upperBound");
    }
    return params;
  }
}
