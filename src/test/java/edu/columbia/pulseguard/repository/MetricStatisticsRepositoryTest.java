package edu.columbia.pulseguard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import edu.columbia.pulseguard.detection.DetectorState;
import edu.columbia.pulseguard.model.MetricStatistics;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Repository tests for {@link MetricStatisticsRepository}, including the locking query.
 */
@DataJpaTest
class MetricStatisticsRepositoryTest {

  @Autowired
  private MetricStatisticsRepository statisticsRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  void findByIdForUpdateLoadsStatistics() {
    statisticsRepository.saveAndFlush(new MetricStatistics(1L));

    assertThat(statisticsRepository.findByIdForUpdate(1L)).isPresent();
    assertThat(statisticsRepository.findByIdForUpdate(99L)).isEmpty();
  }

  @Test
  void appliedStateIsPersisted() {
    Instant ts = Instant.parse("2026-10-01T00:00:00Z");
    MetricStatistics stats = statisticsRepository.saveAndFlush(new MetricStatistics(1L));
    stats.apply(new DetectorState(4, 10.5, 1.5, List.of()), ts);
    statisticsRepository.saveAndFlush(stats);
    entityManager.clear();

    MetricStatistics reloaded = statisticsRepository.findByIdForUpdate(1L).orElseThrow();
    assertThat(reloaded.getSampleCount()).isEqualTo(4);
    assertThat(reloaded.getEwma()).isEqualTo(10.5);
    assertThat(reloaded.getEwmaVariance()).isEqualTo(1.5);
    assertThat(reloaded.getLastTs()).isEqualTo(ts);
  }
}
