package edu.columbia.pulseguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * A metric registered by a client program, together with its detection rule.
 *
 * <p>Metric names are unique per client. Rule parameters are stored as JSON (see
 * {@link ParamsConverter}); valid keys are listed in
 * {@link edu.columbia.pulseguard.detection.RuleParams}.
 */
@Entity
@Table(
    name = "metric",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_metric_client_name", columnNames = {"client_id", "name"}))
public class Metric {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "client_id", nullable = false, length = 100)
  private String clientId;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "unit", length = 50)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 20)
  private DetectionMethod method;

  @Convert(converter = ParamsConverter.class)
  @Column(name = "params", nullable = false, length = 2000)
  private Map<String, Double> params = new HashMap<>();

  @Column(name = "warm_up_samples", nullable = false)
  private int warmUpSamples;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected Metric() {
  }

  /**
   * Creates a new metric; the creation time is set to now.
   *
   * @param clientId the owning client program's ID (from the X-Client-Id header)
   * @param name the metric name, unique for this client
   * @param unit an optional unit label, may be null
   * @param method the detection method
   * @param params the detection parameters, keyed by {@code RuleParams} constants
   * @param warmUpSamples number of initial points that are stored but never flagged
   */
  public Metric(String clientId, String name, String unit, DetectionMethod method,
      Map<String, Double> params, int warmUpSamples) {
    this.clientId = clientId;
    this.name = name;
    this.unit = unit;
    this.method = method;
    this.params = new HashMap<>(params);
    this.warmUpSamples = warmUpSamples;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getClientId() {
    return clientId;
  }

  public String getName() {
    return name;
  }

  public String getUnit() {
    return unit;
  }

  public DetectionMethod getMethod() {
    return method;
  }

  public Map<String, Double> getParams() {
    return Collections.unmodifiableMap(params);
  }

  public int getWarmUpSamples() {
    return warmUpSamples;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Replaces the detection rule (used by updateDetectionRule when dryRun is false).
   *
   * @param method the new detection method
   * @param params the new detection parameters
   * @param warmUpSamples the new warm-up sample count
   */
  public void updateRule(DetectionMethod method, Map<String, Double> params, int warmUpSamples) {
    this.method = method;
    this.params = new HashMap<>(params);
    this.warmUpSamples = warmUpSamples;
  }
}
