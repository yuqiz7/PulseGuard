package edu.columbia.pulseguard.repository;

import edu.columbia.pulseguard.model.DataPoint;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// AI-generated: Claude (Anthropic)
/**
 * Data access for {@link DataPoint}; all queries use the (metric_id, ts) index.
 */
public interface DataPointRepository extends JpaRepository<DataPoint, Long> {

  /**
   * Points of a metric with from &lt;= ts &lt;= to, oldest first.
   *
   * @param metricId the metric ID
   * @param from inclusive start of the range
   * @param to inclusive end of the range
   * @return matching points ordered by timestamp
   */
  List<DataPoint> findByMetricIdAndTsBetweenOrderByTsAsc(Long metricId, Instant from, Instant to);

  /**
   * Most recent points of a metric, newest first; limit with {@code PageRequest.of(0, n)}.
   *
   * <p>Used to load the z-score window and the capped replay history (reverse the result
   * to get oldest first).
   *
   * @param metricId the metric ID
   * @param pageable page size limits how many points are returned
   * @return the newest points, newest first
   */
  List<DataPoint> findByMetricIdOrderByTsDesc(Long metricId, Pageable pageable);

  /**
   * Counts all stored points of a metric.
   *
   * @param metricId the metric ID
   * @return the number of stored points
   */
  long countByMetricId(Long metricId);
}
