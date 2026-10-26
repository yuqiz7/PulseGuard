package edu.columbia.pulseguard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import edu.columbia.pulseguard.model.DataPoint;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

// AI-generated: Claude (Anthropic)
/**
 * Repository tests for {@link DataPointRepository} against an in-memory H2 database.
 */
@DataJpaTest
class DataPointRepositoryTest {

  private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant T1 = T0.plusSeconds(60);
  private static final Instant T2 = T0.plusSeconds(120);

  @Autowired
  private DataPointRepository dataPointRepository;

  @BeforeEach
  void insertPointsOutOfOrder() {
    dataPointRepository.save(new DataPoint(1L, T2, 3.0, false, 0.0));
    dataPointRepository.save(new DataPoint(1L, T0, 1.0, false, 0.0));
    dataPointRepository.save(new DataPoint(1L, T1, 2.0, true, 4.0));
    dataPointRepository.save(new DataPoint(2L, T1, 9.0, false, 0.0));
  }

  @Test
  void rangeQueryIsInclusiveAndOrderedOldestFirst() {
    List<DataPoint> points = dataPointRepository.findByMetricIdAndTsBetweenOrderByTsAsc(1L, T0, T1);

    assertThat(points).extracting(DataPoint::getTs).containsExactly(T0, T1);
  }

  @Test
  void recentPointsAreNewestFirstAndLimited() {
    List<DataPoint> points = dataPointRepository.findByMetricIdOrderByTsDesc(1L,
        PageRequest.of(0, 2));

    assertThat(points).extracting(DataPoint::getValue).containsExactly(3.0, 2.0);
  }

  @Test
  void countIsPerMetric() {
    assertThat(dataPointRepository.countByMetricId(1L)).isEqualTo(3);
    assertThat(dataPointRepository.countByMetricId(2L)).isEqualTo(1);
    assertThat(dataPointRepository.countByMetricId(3L)).isZero();
  }
}
