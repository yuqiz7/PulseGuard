package edu.columbia.pulseguard.detection;

import edu.columbia.pulseguard.api.BadRequestException;
import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// AI-generated: Claude (Anthropic)
/**
 * Validates detection rules sent by clients and fills in default parameter values.
 *
 * <p>Used by registerMetric and updateDetectionRule, so both endpoints accept exactly the same
 * rules.
 */
public final class RuleValidator {

  /** Largest accepted z-score window size. */
  public static final int MAX_WINDOW_SIZE = 10_000;
  /** Largest accepted warm-up sample count. */
  public static final int MAX_WARM_UP_SAMPLES = 10_000;

  private static final Map<DetectionMethod, Set<String>> ALLOWED_KEYS = Map.of(
      DetectionMethod.ZSCORE, Set.of(RuleParams.WINDOW_SIZE, RuleParams.Z_THRESHOLD),
      DetectionMethod.EWMA, Set.of(RuleParams.ALPHA, RuleParams.TOLERANCE),
      DetectionMethod.THRESHOLD, Set.of(RuleParams.LOWER_BOUND, RuleParams.UPPER_BOUND));

  private RuleValidator() {
  }

  /**
   * Validates the parameters for a method and returns them with defaults applied.
   *
   * @param method the detection method
   * @param params the parameters sent by the client; null means none
   * @return a new map containing every parameter the method uses
   * @throws BadRequestException if a key is unknown for the method or a value is invalid
   */
  public static Map<String, Double> normalizeParams(DetectionMethod method,
      Map<String, Double> params) {
    Map<String, Double> result = new HashMap<>();
    if (params != null) {
      Set<String> allowed = ALLOWED_KEYS.get(method);
      for (Map.Entry<String, Double> entry : params.entrySet()) {
        if (!allowed.contains(entry.getKey())) {
          throw new BadRequestException("Unknown parameter '" + entry.getKey() + "' for "
              + method + "; allowed: " + new TreeSet<>(allowed));
        }
        if (entry.getValue() == null || !Double.isFinite(entry.getValue())) {
          throw new BadRequestException("Parameter '" + entry.getKey() + "' must be a number");
        }
        result.put(entry.getKey(), entry.getValue());
      }
    }
    switch (method) {
      case ZSCORE:
        return normalizeZscore(result);
      case EWMA:
        return normalizeEwma(result);
      case THRESHOLD:
        return normalizeThreshold(result);
      default:
        throw new BadRequestException("Unsupported detection method: " + method);
    }
  }

  /**
   * Validates the warm-up sample count, defaulting to 0 for THRESHOLD and 10 otherwise.
   *
   * @param method the detection method
   * @param warmUpSamples the value sent by the client; null means use the default
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
    if (lower == null && upper == null) {
      throw new BadRequestException("THRESHOLD requires lowerBound and/or upperBound");
    }
    if (lower != null && upper != null && lower >= upper) {
      throw new BadRequestException("lowerBound must be less than upperBound");
    }
    return params;
  }
}
