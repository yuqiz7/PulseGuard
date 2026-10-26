package edu.columbia.pulseguard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import edu.columbia.pulseguard.model.Anomaly;
import edu.columbia.pulseguard.model.DetectionMethod;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

// AI-generated: Claude (Anthropic)
/**
 * Repository tests for {@link AnomalyRepository} against an in-memory H2 database.
 */
@DataJpaTest
class AnomalyRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant END = T0.plusSeconds(3600);

  @Autowired
  private AnomalyRepository anomalyRepository;

  @BeforeEach
  void insertAnomalies() {
    anomalyRepository.save(anomaly(1L, T0.plusSeconds(30), 5.0));
    anomalyRepository.save(anomaly(1L, T0.plusSeconds(10), 2.0));
    anomalyRepository.save(anomaly(1L, T0.plusSeconds(20), 4.0));
    anomalyRepository.save(anomaly(2L, T0.plusSeconds(20), 9.0));
  }

  private static Anomaly anomaly(Long metricId, Instant ts, double score) {
    return new Anomaly(metricId, ts, 100.0, score, 0.0, 10.0, DetectionMethod.ZSCORE);
  }

  @Test
  void filtersByMinScoreAndOrdersOldestFirst() {
    List<Anomaly> result = anomalyRepository
        .findByMetricIdAndTsBetweenAndScoreGreaterThanEqualOrderByTsAsc(
            1L, T0, END, 3.0, PageRequest.of(0, 100));

    assertThat(result).extracting(Anomaly::getScore).containsExactly(4.0, 5.0);
  }

  @Test
  void pageSizeLimitsResultButNotCount() {
    List<Anomaly> result = anomalyRepository
        .findByMetricIdAndTsBetweenAndScoreGreaterThanEqualOrderByTsAsc(
            1L, T0, END, 0.0, PageRequest.of(0, 1));

    assertThat(result).hasSize(1);
    assertThat(anomalyRepository.countByMetricIdAndTsBetweenAndScoreGreaterThanEqual(
        1L, T0, END, 0.0)).isEqualTo(3);
  }

  @Test
  void deleteByMetricIdOnlyRemovesThatMetric() {
    assertThat(anomalyRepository.deleteByMetricId(1L)).isEqualTo(3);
    assertThat(anomalyRepository.count()).isEqualTo(1);
  }
}
