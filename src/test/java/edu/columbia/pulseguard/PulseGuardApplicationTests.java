package edu.columbia.pulseguard;

import static org.assertj.core.api.Assertions.assertThat;

import edu.columbia.pulseguard.detection.AnomalyDetector;
import edu.columbia.pulseguard.service.DataIngestionService;
import edu.columbia.pulseguard.service.MetricLookupService;
import edu.columbia.pulseguard.service.MetricRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Verifies that the full application context starts (against the in-memory test database).
 */
@SpringBootTest
class PulseGuardApplicationTests {

  @Autowired
  private ApplicationContext context;

  // The detection engine is provided by Area 2; a mock keeps this test independent of it.
  @MockitoBean
  private AnomalyDetector detector;

  @Test
  void contextLoadsWithSharedServices() {
    assertThat(context.getBean(MetricLookupService.class)).isNotNull();
    assertThat(context.getBean(MetricRegistrationService.class)).isNotNull();
    assertThat(context.getBean(DataIngestionService.class)).isNotNull();
  }
}
