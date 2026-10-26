package edu.columbia.pulseguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

// AI-generated: Claude (Anthropic)
/**
 * One submitted value of a metric, with the verdict computed when it was ingested.
 */
@Entity
@Table(
    name = "data_point",
    indexes = @Index(name = "idx_data_point_metric_ts", columnList = "metric_id, ts"))
public class DataPoint {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "metric_id", nullable = false)
  private Long metricId;

  @Column(name = "ts", nullable = false)
  private Instant ts;

  @Column(name = "point_value", nullable = false)
  private double value;

  @Column(name = "is_anomaly", nullable = false)
  private boolean anomaly;

  @Column(name = "score", nullable = false)
  private double score;

  /** Required by JPA. */
  protected DataPoint() {
  }

  /**
   * Creates a data point.
   *
   * @param metricId the metric this point belongs to
   * @param ts the point's timestamp
   * @param value the submitted value
   * @param anomaly whether the point was flagged as an anomaly
   * @param score the anomaly score computed for the point
   */
  public DataPoint(Long metricId, Instant ts, double value, boolean anomaly, double score) {
    this.metricId = metricId;
    this.ts = ts;
    this.value = value;
    this.anomaly = anomaly;
    this.score = score;
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

  public boolean isAnomaly() {
    return anomaly;
  }

  public double getScore() {
    return score;
  }

  /**
   * Overwrites the verdict (used when a rule change is applied and history is replayed).
   *
   * @param anomaly the new anomaly flag
   * @param score the new anomaly score
   */
  public void setVerdict(boolean anomaly, double score) {
    this.anomaly = anomaly;
    this.score = score;
  }
}
