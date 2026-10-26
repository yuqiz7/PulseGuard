package edu.columbia.pulseguard;

import static org.assertj.core.api.Assertions.assertThat;

import edu.columbia.pulseguard.service.MetricLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

// AI-generated: Claude (Anthropic)
/**
 * Verifies that the full application context starts (against the in-memory test database).
 */
@SpringBootTest
class PulseGuardApplicationTests {

  @Autowired
  private ApplicationContext context;

  @Test
  void contextLoadsWithSharedServices() {
    assertThat(context.getBean(MetricLookupService.class)).isNotNull();
  }
}
