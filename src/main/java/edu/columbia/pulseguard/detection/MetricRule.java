package edu.columbia.pulseguard.detection;

import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import java.util.Map;
import java.util.Objects;

// AI-generated: Claude (Anthropic)
/**
 * Immutable detection rule passed to the detection engine.
 *
 * @param method the detection method
 * @param params detection parameters keyed by {@link RuleParams} constants
 * @param warmUpSamples number of initial points that are never flagged
 */
public record MetricRule(DetectionMethod method, Map<String, Double> params, int warmUpSamples) {

  /**
   * Validates the components and copies the parameter map.
   */
  public MetricRule {
    Objects.requireNonNull(method, "method");
    params = Map.copyOf(Objects.requireNonNull(params, "params"));
  }

  /**
   * Builds the rule currently configured on a metric.
   *
   * @param metric the metric
   * @return the metric's rule
   */
  public static MetricRule fromMetric(Metric metric) {
    return new MetricRule(metric.getMethod(), metric.getParams(), metric.getWarmUpSamples());
  }

  /**
   * Returns a parameter value, or a default when it is not set.
   *
   * @param key a {@link RuleParams} key
   * @param defaultValue the value to use when the key is absent
   * @return the configured or default value
   */
  public double param(String key, double defaultValue) {
    return params.getOrDefault(key, defaultValue);
  }
}
