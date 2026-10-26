package edu.columbia.pulseguard.model;

import edu.columbia.pulseguard.detection.DetectorState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

// AI-generated: Claude (Anthropic)
/**
 * Incrementally maintained statistics of one metric (one row per metric).
 *
 * <p>Ingestion and rule updates lock this row with
 * {@link edu.columbia.pulseguard.repository.MetricStatisticsRepository#findByIdForUpdate},
 * so concurrent batches for the same metric are processed one at a time.
 */
@Entity
@Table(name = "metric_statistics")
public class MetricStatistics {

  @Id
  @Column(name = "metric_id")
  private Long metricId;

  @Column(name = "sample_count", nullable = false)
  private long sampleCount;

  @Column(name = "ewma")
  private Double ewma;

  @Column(name = "ewma_variance")
  private Double ewmaVariance;

  @Column(name = "last_ts")
  private Instant lastTs;

  /** Required by JPA. */
  protected MetricStatistics() {
  }

  /**
   * Creates empty statistics for a newly registered metric.
   *
   * @param metricId the metric these statistics belong to
   */
  public MetricStatistics(Long metricId) {
    this.metricId = metricId;
  }

  public Long getMetricId() {
    return metricId;
  }

  public long getSampleCount() {
    return sampleCount;
  }

  public Double getEwma() {
    return ewma;
  }

  public Double getEwmaVariance() {
    return ewmaVariance;
  }

  /**
   * Timestamp of the latest stored point; new points must be strictly later.
   *
   * @return the latest timestamp, or null if no point has been stored yet
   */
  public Instant getLastTs() {
    return lastTs;
  }

  /**
   * Builds the detector state from these statistics and the most recent stored values.
   *
   * @param recentValues the last windowSize stored values, oldest first
   * @return the state to pass to the detection engine
   */
  public DetectorState toDetectorState(List<Double> recentValues) {
    return new DetectorState(sampleCount, ewma, ewmaVariance, recentValues);
  }

  /**
   * Saves the detector state produced after processing points.
   *
   * @param state the updated detector state
   * @param latestTs timestamp of the last processed point, or null if none
   */
  public void apply(DetectorState state, Instant latestTs) {
    this.sampleCount = state.sampleCount();
    this.ewma = state.ewma();
    this.ewmaVariance = state.ewmaVariance();
    this.lastTs = latestTs;
  }
}
