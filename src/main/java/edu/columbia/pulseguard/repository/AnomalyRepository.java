package edu.columbia.pulseguard.repository;

import edu.columbia.pulseguard.model.Anomaly;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// AI-generated: Claude (Anthropic)
/**
 * Data access for {@link Anomaly}.
 */
public interface AnomalyRepository extends JpaRepository<Anomaly, Long> {

  /**
   * Anomalies of a metric in a time range with at least the given score, oldest first.
   *
   * @param metricId the metric ID
   * @param from inclusive start of the range
   * @param to inclusive end of the range
   * @param minScore minimum anomaly score (use 0 for all)
   * @param pageable page size limits how many anomalies are returned
   * @return matching anomalies ordered by timestamp
   */
  List<Anomaly> findByMetricIdAndTsBetweenAndScoreGreaterThanEqualOrderByTsAsc(
      Long metricId, Instant from, Instant to, double minScore, Pageable pageable);

  /**
   * Counts the anomalies matching the same filter, ignoring the page size.
   *
   * @param metricId the metric ID
   * @param from inclusive start of the range
   * @param to inclusive end of the range
   * @param minScore minimum anomaly score
   * @return total number of matching anomalies
   */
  long countByMetricIdAndTsBetweenAndScoreGreaterThanEqual(
      Long metricId, Instant from, Instant to, double minScore);

  /**
   * Deletes all anomalies of a metric (used when a new rule is applied); requires a transaction.
   *
   * @param metricId the metric ID
   * @return the number of deleted anomalies
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from Anomaly a where a.metricId = :metricId")
  int deleteByMetricId(@Param("metricId") Long metricId);

  /**
   * Deletes a metric's anomalies at or after a timestamp (used when a new rule is applied to the
   * replayed part of the history); requires a transaction.
   *
   * @param metricId the metric ID
   * @param from inclusive start of the deleted range
   * @return the number of deleted anomalies
   */
  @Modifying(flushAutomatically = true)
  @Query("delete from Anomaly a where a.metricId = :metricId and a.ts >= :from")
  int deleteByMetricIdFrom(@Param("metricId") Long metricId, @Param("from") Instant from);
}
