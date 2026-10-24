package edu.columbia.pulseguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * A detected anomaly, stored so that clients can query anomalies without rescanning points.
 */
@Entity
@Table(
    name = "anomaly",
    indexes = @Index(name = "idx_anomaly_metric_ts", columnList = "metric_id, ts"))
public class Anomaly {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "metric_id", nullable = false)
  private Long metricId;

  @Column(name = "ts", nullable = false)
  private Instant ts;

  @Column(name = "point_value", nullable = false)
  private double value;

  @Column(name = "score", nullable = false)
  private double score;

  @Column(name = "expected_low", nullable = false)
  private double expectedLow;

  @Column(name = "expected_high", nullable = false)
  private double expectedHigh;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 20)
  private DetectionMethod method;

  /** Required by JPA. */
  protected Anomaly() {
  }

  /**
   * Creates an anomaly record.
   *
   * @param metricId the metric the anomaly belongs to
   * @param ts the timestamp of the anomalous point
   * @param value the anomalous value
   * @param score the anomaly score
   * @param expectedLow lower end of the expected range at that time
   * @param expectedHigh upper end of the expected range at that time
   * @param method the detection method in effect when the anomaly was detected
   */
  public Anomaly(Long metricId, Instant ts, double value, double score, double expectedLow,
      double expectedHigh, DetectionMethod method) {
    this.metricId = metricId;
    this.ts = ts;
    this.value = value;
    this.score = score;
    this.expectedLow = expectedLow;
    this.expectedHigh = expectedHigh;
    this.method = method;
  }

  public Long getId() {
    return id;
  }

  public Long getMetricId() {
    return metricId;
  }

  public Instant getTs() {
    return ts;
  }

  public double getValue() {
    return value;
  }

  public double getScore() {
    return score;
  }

  public double getExpectedLow() {
    return expectedLow;
  }

  public double getExpectedHigh() {
    return expectedHigh;
  }

  public DetectionMethod getMethod() {
    return method;
  }
}
