package edu.columbia.pulseguard.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.columbia.pulseguard.detection.RuleParams;
import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

// AI-generated: Claude (Anthropic)
/**
 * Repository tests for {@link MetricRepository} against an in-memory H2 database.
 */
@DataJpaTest
class MetricRepositoryTest {

  @Autowired
  private MetricRepository metricRepository;

  @Autowired
  private TestEntityManager entityManager;

  private Metric newMetric(String clientId, String name) {
    return new Metric(clientId, name, "%", DetectionMethod.ZSCORE,
        Map.of(RuleParams.WINDOW_SIZE, 20.0, RuleParams.Z_THRESHOLD, 3.0), 5);
  }

  @Test
  void findByIdAndClientIdOnlyReturnsOwnersMetric() {
    Metric saved = metricRepository.save(newMetric("client-a", "cpu"));

    assertThat(metricRepository.findByIdAndClientId(saved.getId(), "client-a")).isPresent();
    assertThat(metricRepository.findByIdAndClientId(saved.getId(), "client-b")).isEmpty();
  }

  @Test
  void existsByClientIdAndNameIsScopedToClient() {
    metricRepository.save(newMetric("client-a", "cpu"));

    assertThat(metricRepository.existsByClientIdAndName("client-a", "cpu")).isTrue();
    assertThat(metricRepository.existsByClientIdAndName("client-b", "cpu")).isFalse();
    assertThat(metricRepository.existsByClientIdAndName("client-a", "memory")).isFalse();
  }

  @Test
  void sameNameIsAllowedForDifferentClients() {
    metricRepository.saveAndFlush(newMetric("client-a", "cpu"));
    metricRepository.saveAndFlush(newMetric("client-b", "cpu"));

    assertThat(metricRepository.count()).isEqualTo(2);
  }

  @Test
  void duplicateNameForSameClientViolatesUniqueConstraint() {
    metricRepository.saveAndFlush(newMetric("client-a", "cpu"));

    assertThatThrownBy(() -> metricRepository.saveAndFlush(newMetric("client-a", "cpu")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void ruleParametersSurviveReload() {
    Long id = metricRepository.saveAndFlush(newMetric("client-a", "cpu")).getId();
    entityManager.clear();

    Metric reloaded = metricRepository.findById(id).orElseThrow();
    assertThat(reloaded.getParams())
        .containsEntry(RuleParams.WINDOW_SIZE, 20.0)
        .containsEntry(RuleParams.Z_THRESHOLD, 3.0);
    assertThat(reloaded.getMethod()).isEqualTo(DetectionMethod.ZSCORE);
  }
}
