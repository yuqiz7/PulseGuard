package edu.columbia.pulseguard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.columbia.pulseguard.detection.AnomalyDetector;
import edu.columbia.pulseguard.detection.DetectionResult;
import edu.columbia.pulseguard.detection.DetectorState;
import edu.columbia.pulseguard.detection.MetricRule;
import edu.columbia.pulseguard.model.MetricStatistics;
import edu.columbia.pulseguard.repository.AnomalyRepository;
import edu.columbia.pulseguard.repository.DataPointRepository;
import edu.columbia.pulseguard.repository.MetricStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * End-to-end test of registerMetric and submitDataPoints through the real controllers, services,
 * repositories and the in-memory database. Only the detection engine (Area 2) is stubbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationAndIngestionFlowTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private DataPointRepository dataPointRepository;
  @Autowired
  private AnomalyRepository anomalyRepository;
  @Autowired
  private MetricStatisticsRepository statisticsRepository;

  @MockitoBean
  private AnomalyDetector detector;

  @BeforeEach
  void stubDetector() {
    // Flags values above 100.
    when(detector.evaluate(any(MetricRule.class), any(DetectorState.class), anyDouble()))
        .thenAnswer(call -> {
          double value = call.getArgument(2);
          return new DetectionResult(value > 100, Math.max(0, value - 100), 0, 100);
        });
    when(detector.update(any(MetricRule.class), any(DetectorState.class), anyDouble()))
        .thenAnswer(call -> {
          DetectorState state = call.getArgument(1);
          return new DetectorState(state.sampleCount() + 1, state.ewma(), state.ewmaVariance(),
              state.window());
        });
  }

  private static MockHttpServletRequestBuilder json(String client, String path, String body) {
    return post(path).header("X-Client-Id", client)
        .contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private long register(String client, String name) throws Exception {
    String body = "{\"name\":\"" + name + "\",\"method\":\"THRESHOLD\","
        + "\"params\":{\"lowerBound\":0,\"upperBound\":100}}";
    String response = mockMvc.perform(json(client, "/metrics", body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.warmUpSamples").value(0))
        .andReturn().getResponse().getContentAsString();
    JsonNode node = objectMapper.readTree(response);
    return node.get("id").asLong();
  }

  @Test
  void registerThenSubmitStoresPointsAnomaliesAndStatistics() throws Exception {
    long id = register("flow-client", "flow-metric");

    String batch = "{\"dataPoints\":["
        + "{\"timestamp\":\"2026-10-20T12:00:00Z\",\"value\":10},"
        + "{\"timestamp\":\"2026-10-20T12:01:00Z\",\"value\":150}]}";
    mockMvc.perform(json("flow-client", "/metrics/" + id + "/datapoints", batch))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(2))
        .andExpect(jsonPath("$.anomalies").value(1))
        .andExpect(jsonPath("$.results[1].anomaly").value(true));

    assertThat(dataPointRepository.countByMetricId(id)).isEqualTo(2);
    assertThat(anomalyRepository.findAll()).filteredOn(a -> a.getMetricId() == id)
        .singleElement().satisfies(a -> assertThat(a.getValue()).isEqualTo(150.0));
    MetricStatistics statistics = statisticsRepository.findById(id).orElseThrow();
    assertThat(statistics.getSampleCount()).isEqualTo(2);
    assertThat(statistics.getLastTs().toString()).isEqualTo("2026-10-20T12:01:00Z");

    // A second batch must continue after the stored last timestamp.
    String stale = "{\"dataPoints\":[{\"timestamp\":\"2026-10-20T12:01:00Z\",\"value\":1}]}";
    mockMvc.perform(json("flow-client", "/metrics/" + id + "/datapoints", stale))
        .andExpect(status().isConflict());
    assertThat(dataPointRepository.countByMetricId(id)).isEqualTo(2);
  }

  @Test
  void duplicateNameIsConflictButOtherClientsMayReuseIt() throws Exception {
    register("dup-client-1", "shared-name");
    String body = "{\"name\":\"shared-name\",\"method\":\"EWMA\"}";

    mockMvc.perform(json("dup-client-1", "/metrics", body)).andExpect(status().isConflict());
    mockMvc.perform(json("dup-client-2", "/metrics", body)).andExpect(status().isCreated())
        .andExpect(jsonPath("$.params.alpha").value(0.3))
        .andExpect(jsonPath("$.warmUpSamples").value(10));
  }

  @Test
  void anotherClientsMetricIsNotFound() throws Exception {
    long id = register("owner-client", "private-metric");
    String batch = "{\"dataPoints\":[{\"timestamp\":\"2026-10-20T12:00:00Z\",\"value\":1}]}";

    mockMvc.perform(json("intruder", "/metrics/" + id + "/datapoints", batch))
        .andExpect(status().isNotFound());
    assertThat(dataPointRepository.countByMetricId(id)).isZero();
  }
}
