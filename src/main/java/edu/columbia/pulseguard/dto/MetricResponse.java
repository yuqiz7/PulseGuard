package edu.columbia.pulseguard.dto;

import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

// AI-generated: Claude (Anthropic)
/**
 * A registered metric as returned to clients.
 *
 * @param metricId the metric ID to use in later calls
 * @param name the metric name
 * @param unit the unit label, may be null
 * @param method the detection method
 * @param params the stored detection parameters, including defaults
 * @param warmUpSamples the warm-up sample count
 * @param createdAt when the metric was registered
 */
public record MetricResponse(Long metricId, String name, String unit, DetectionMethod method,
    Map<String, Double> params, int warmUpSamples, Instant createdAt) {

  /**
   * Builds the response for a stored metric.
   *
   * @param metric the stored metric
   * @return the response body
   */
  public static MetricResponse from(Metric metric) {
    return new MetricResponse(metric.getId(), metric.getName(), metric.getUnit(),
        metric.getMethod(), new TreeMap<>(metric.getParams()), metric.getWarmUpSamples(),
        metric.getCreatedAt());
  }
}
