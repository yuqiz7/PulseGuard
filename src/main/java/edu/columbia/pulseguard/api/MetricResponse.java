package edu.columbia.pulseguard.api;

import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import java.time.Instant;
import java.util.Map;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Response body describing a registered metric.
 *
 * @param id the metric ID used in all other endpoints
 * @param name the metric name
 * @param unit the unit label, may be null
 * @param method the detection method
 * @param params the effective detection parameters, including defaults
 * @param warmUpSamples the effective warm-up sample count
 * @param createdAt when the metric was registered
 */
public record MetricResponse(Long id, String name, String unit, DetectionMethod method,
    Map<String, Double> params, int warmUpSamples, Instant createdAt) {

  /**
   * Builds the response for a stored metric.
   *
   * @param metric the metric
   * @return the response body
   */
  public static MetricResponse from(Metric metric) {
    return new MetricResponse(metric.getId(), metric.getName(), metric.getUnit(),
        metric.getMethod(), metric.getParams(), metric.getWarmUpSamples(),
        metric.getCreatedAt());
  }
}
