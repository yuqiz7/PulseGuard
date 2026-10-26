package edu.columbia.pulseguard.repository;

import edu.columbia.pulseguard.model.Metric;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// AI-generated: Claude (Anthropic)
/**
 * Data access for {@link Metric}.
 */
public interface MetricRepository extends JpaRepository<Metric, Long> {

  /**
   * Finds a metric only if it belongs to the given client (per-client isolation).
   *
   * @param id the metric ID
   * @param clientId the calling client's ID
   * @return the metric, or empty if it does not exist or belongs to another client
   */
  Optional<Metric> findByIdAndClientId(Long id, String clientId);

  /**
   * Checks whether the client already has a metric with this name.
   *
   * @param clientId the calling client's ID
   * @param name the metric name
   * @return true if the name is taken for this client
   */
  boolean existsByClientIdAndName(String clientId, String name);
}
