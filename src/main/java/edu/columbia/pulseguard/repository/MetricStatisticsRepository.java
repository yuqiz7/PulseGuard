package edu.columbia.pulseguard.repository;

import edu.columbia.pulseguard.model.MetricStatistics;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Data access for {@link MetricStatistics}.
 */
public interface MetricStatisticsRepository extends JpaRepository<MetricStatistics, Long> {

  /**
   * Loads a metric's statistics and locks the row until the current transaction ends.
   *
   * <p>Call inside a {@code @Transactional} method; concurrent callers for the same metric wait
   * until the lock is released.
   *
   * @param metricId the metric ID
   * @return the locked statistics, or empty if none exist
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from MetricStatistics s where s.metricId = :metricId")
  Optional<MetricStatistics> findByIdForUpdate(@Param("metricId") Long metricId);
}
