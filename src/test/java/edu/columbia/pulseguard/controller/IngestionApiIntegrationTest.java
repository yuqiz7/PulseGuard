package edu.columbia.pulseguard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.columbia.pulseguard.api.ApiHeaders;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// AI-generated: Claude (Anthropic)
/**
 * End-to-end tests of registerMetric and submitDataPoints through the full application
 * (controllers, services, detection engine and the in-memory test database).
 */
@SpringBootTest
@AutoConfigureMockMvc
class IngestionApiIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private long register(String clientId, String body) throws Exception {
    String response = mockMvc.perform(post("/metrics").header(ApiHeaders.CLIENT_ID, clientId)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    JsonNode json = objectMapper.readTree(response);
    return json.get("metricId").asLong();
  }

  @Test
  void registerThenSubmitFlagsThresholdViolations() throws Exception {
    String client = "client-" + UUID.randomUUID();
    long metricId = register(client,
        "{\"name\":\"temp\",\"method\":\"THRESHOLD\",\"params\":{\"upperBound\":30}}");

    mockMvc.perform(post("/metrics/" + metricId + "/datapoints")
            .header(ApiHeaders.CLIENT_ID, client)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"points\":[{\"timestamp\":\"2026-10-01T00:00:00Z\",\"value\":25},"
                + "{\"timestamp\":\"2026-10-01T00:01:00Z\",\"value\":31.5}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(2))
        .andExpect(jsonPath("$.anomalies").value(1))
        .andExpect(jsonPath("$.results[1].anomaly").value(true))
        .andExpect(jsonPath("$.results[1].score").value(1.5))
        .andExpect(jsonPath("$.results[0].timestamp").value("2026-10-01T00:00:00Z"));
  }

  @Test
  void registrationAppliesDefaults() throws Exception {
    String client = "client-" + UUID.randomUUID();

    mockMvc.perform(post("/metrics").header(ApiHeaders.CLIENT_ID, client)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"cpu\",\"method\":\"ZSCORE\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.params.windowSize").value(30.0))
        .andExpect(jsonPath("$.params.zThreshold").value(3.0))
        .andExpect(jsonPath("$.warmUpSamples").value(10));
  }

  @Test
  void duplicateNameIsConflictButOtherClientMayReuseIt() throws Exception {
    String client = "client-" + UUID.randomUUID();
    String body = "{\"name\":\"cpu\",\"method\":\"EWMA\"}";
    register(client, body);

    mockMvc.perform(post("/metrics").header(ApiHeaders.CLIENT_ID, client)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
    register("other-" + UUID.randomUUID(), body);
  }

  @Test
  void invalidRuleIsBadRequest() throws Exception {
    mockMvc.perform(post("/metrics").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"cpu\",\"method\":\"EWMA\",\"params\":{\"alpha\":1.5}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("alpha must be between 0 and 1 (exclusive)"));
  }

  @Test
  void anotherClientCannotSubmitToMetric() throws Exception {
    long metricId = register("owner-" + UUID.randomUUID(),
        "{\"name\":\"cpu\",\"method\":\"THRESHOLD\",\"params\":{\"upperBound\":1}}");

    mockMvc.perform(post("/metrics/" + metricId + "/datapoints")
            .header(ApiHeaders.CLIENT_ID, "intruder")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"points\":[{\"timestamp\":\"2026-10-01T00:00:00Z\",\"value\":5}]}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void outOfOrderSubmissionIsConflict() throws Exception {
    String client = "client-" + UUID.randomUUID();
    long metricId = register(client,
        "{\"name\":\"cpu\",\"method\":\"THRESHOLD\",\"params\":{\"upperBound\":1}}");
    String later = "{\"points\":[{\"timestamp\":\"2026-10-02T00:00:00Z\",\"value\":0}]}";
    String earlier = "{\"points\":[{\"timestamp\":\"2026-10-01T00:00:00Z\",\"value\":0}]}";

    mockMvc.perform(post("/metrics/" + metricId + "/datapoints")
            .header(ApiHeaders.CLIENT_ID, client)
            .contentType(MediaType.APPLICATION_JSON).content(later))
        .andExpect(status().isOk());
    mockMvc.perform(post("/metrics/" + metricId + "/datapoints")
            .header(ApiHeaders.CLIENT_ID, client)
            .contentType(MediaType.APPLICATION_JSON).content(earlier))
        .andExpect(status().isConflict());
  }
}
