package edu.columbia.pulseguard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.columbia.pulseguard.api.ApiHeaders;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// AI-generated: Claude (Anthropic)
/**
 * End-to-end tests of queryAnomalies, getMetricSummary and updateDetectionRule through the
 * full application. Each test registers a THRESHOLD metric (upper bound 50) and submits the
 * values 10, 20, 60, 40 and 80 one minute apart, so 60 and 80 are anomalies.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QueryApiIntegrationTest {

  private static final String FROM = "2026-10-01T00:00:00Z";
  private static final String TO = "2026-10-01T23:59:59Z";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private String client;

  private long metricId;

  @BeforeEach
  void registerAndSubmit() throws Exception {
    client = "client-" + UUID.randomUUID();
    String created = mockMvc.perform(post("/metrics").header(ApiHeaders.CLIENT_ID, client)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"temp\",\"method\":\"THRESHOLD\",\"params\":{\"upperBound\":50}}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    metricId = objectMapper.readTree(created).get("metricId").asLong();

    mockMvc.perform(post("/metrics/" + metricId + "/datapoints")
            .header(ApiHeaders.CLIENT_ID, client)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"points\":["
                + "{\"timestamp\":\"2026-10-01T10:00:00Z\",\"value\":10},"
                + "{\"timestamp\":\"2026-10-01T10:01:00Z\",\"value\":20},"
                + "{\"timestamp\":\"2026-10-01T10:02:00Z\",\"value\":60},"
                + "{\"timestamp\":\"2026-10-01T10:03:00Z\",\"value\":40},"
                + "{\"timestamp\":\"2026-10-01T11:00:00Z\",\"value\":80}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.anomalies").value(2));
  }

  @Test
  void queryAnomaliesReturnsStoredAnomalies() throws Exception {
    mockMvc.perform(get("/metrics/" + metricId + "/anomalies").header(ApiHeaders.CLIENT_ID, client)
            .param("from", FROM).param("to", TO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.anomalies[0].value").value(60.0))
        .andExpect(jsonPath("$.anomalies[1].value").value(80.0))
        .andExpect(jsonPath("$.anomalies[0].method").value("THRESHOLD"));
  }

  @Test
  void queryAnomaliesFiltersByScoreAndLimit() throws Exception {
    mockMvc.perform(get("/metrics/" + metricId + "/anomalies").header(ApiHeaders.CLIENT_ID, client)
            .param("from", FROM).param("to", TO).param("minScore", "20"))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.anomalies[0].value").value(80.0));
    mockMvc.perform(get("/metrics/" + metricId + "/anomalies").header(ApiHeaders.CLIENT_ID, client)
            .param("from", FROM).param("to", TO).param("limit", "1"))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.anomalies.length()").value(1));
  }

  @Test
  void summaryReturnsOverallAndHourlyStatistics() throws Exception {
    mockMvc.perform(get("/metrics/" + metricId + "/summary").header(ApiHeaders.CLIENT_ID, client)
            .param("from", FROM).param("to", TO).param("bucket", "hour"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overall.count").value(5))
        .andExpect(jsonPath("$.overall.mean").value(42.0))
        .andExpect(jsonPath("$.overall.max").value(80.0))
        .andExpect(jsonPath("$.overall.anomalyRate").value(0.4))
        .andExpect(jsonPath("$.buckets.length()").value(2))
        .andExpect(jsonPath("$.buckets[0].start").value("2026-10-01T10:00:00Z"))
        .andExpect(jsonPath("$.buckets[0].stats.count").value(4));
  }

  @Test
  void dryRunReportsEffectWithoutChangingAnything() throws Exception {
    mockMvc.perform(put("/metrics/" + metricId + "/rule").header(ApiHeaders.CLIENT_ID, client)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"method\":\"THRESHOLD\",\"params\":{\"upperBound\":30}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(false))
        .andExpect(jsonPath("$.previousAnomalyCount").value(2))
        .andExpect(jsonPath("$.newAnomalyCount").value(3))
        .andExpect(jsonPath("$.changedPointCount").value(1))
        .andExpect(jsonPath("$.changedPoints[0].value").value(40.0));

    mockMvc.perform(get("/metrics/" + metricId + "/anomalies").header(ApiHeaders.CLIENT_ID, client)
            .param("from", FROM).param("to", TO))
        .andExpect(jsonPath("$.total").value(2));
  }

  @Test
  void applyingRuleRecomputesStoredAnomalies() throws Exception {
    mockMvc.perform(put("/metrics/" + metricId + "/rule").param("dryRun", "false")
            .header(ApiHeaders.CLIENT_ID, client)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"method\":\"THRESHOLD\",\"params\":{\"upperBound\":30}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(true));

    mockMvc.perform(get("/metrics/" + metricId + "/anomalies").header(ApiHeaders.CLIENT_ID, client)
            .param("from", FROM).param("to", TO))
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.anomalies[1].value").value(40.0));
  }

  @Test
  void otherClientCannotQueryOrUpdate() throws Exception {
    mockMvc.perform(get("/metrics/" + metricId + "/summary").header(ApiHeaders.CLIENT_ID, "x")
            .param("from", FROM).param("to", TO))
        .andExpect(status().isNotFound());
    mockMvc.perform(put("/metrics/" + metricId + "/rule").header(ApiHeaders.CLIENT_ID, "x")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"method\":\"EWMA\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void invertedRangeIsBadRequest() throws Exception {
    mockMvc.perform(get("/metrics/" + metricId + "/anomalies").header(ApiHeaders.CLIENT_ID, client)
            .param("from", TO).param("to", FROM))
        .andExpect(status().isBadRequest());
  }
}
