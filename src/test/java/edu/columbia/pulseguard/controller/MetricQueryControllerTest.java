package edu.columbia.pulseguard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.columbia.pulseguard.api.ApiHeaders;
import edu.columbia.pulseguard.api.GlobalExceptionHandler;
import edu.columbia.pulseguard.dto.AnomalyQueryResponse;
import edu.columbia.pulseguard.dto.MetricSummaryResponse;
import edu.columbia.pulseguard.dto.RuleUpdateResponse;
import edu.columbia.pulseguard.dto.SummaryStats;
import edu.columbia.pulseguard.dto.UpdateRuleRequest;
import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.service.AnomalyQueryService;
import edu.columbia.pulseguard.service.MetricSummaryService;
import edu.columbia.pulseguard.service.RuleUpdateService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// AI-generated: Claude (Anthropic)
/**
 * Web-layer tests for {@link MetricQueryController}, with the services mocked.
 */
@ExtendWith(MockitoExtension.class)
class MetricQueryControllerTest {

  private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");

  @Mock
  private AnomalyQueryService anomalyQueryService;

  @Mock
  private MetricSummaryService summaryService;

  @Mock
  private RuleUpdateService ruleUpdateService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .standaloneSetup(new MetricQueryController(
            anomalyQueryService, summaryService, ruleUpdateService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void queryAnomaliesUsesDefaults() throws Exception {
    when(anomalyQueryService.query("c", 5L, FROM, TO, 0.0, 100))
        .thenReturn(new AnomalyQueryResponse(5L, 0, List.of()));

    mockMvc.perform(get("/metrics/5/anomalies").header(ApiHeaders.CLIENT_ID, "c")
            .param("from", FROM.toString()).param("to", TO.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void queryAnomaliesPassesFilters() throws Exception {
    when(anomalyQueryService.query("c", 5L, FROM, TO, 2.5, 10))
        .thenReturn(new AnomalyQueryResponse(5L, 3, List.of()));

    mockMvc.perform(get("/metrics/5/anomalies").header(ApiHeaders.CLIENT_ID, "c")
            .param("from", FROM.toString()).param("to", TO.toString())
            .param("minScore", "2.5").param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3));
  }

  @Test
  void missingFromReturns400() throws Exception {
    mockMvc.perform(get("/metrics/5/anomalies").header(ApiHeaders.CLIENT_ID, "c")
            .param("to", TO.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Missing query parameter: from"));
    verifyNoInteractions(anomalyQueryService);
  }

  @Test
  void malformedTimestampReturns400() throws Exception {
    mockMvc.perform(get("/metrics/5/anomalies").header(ApiHeaders.CLIENT_ID, "c")
            .param("from", "last tuesday").param("to", TO.toString()))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(anomalyQueryService);
  }

  @Test
  void summaryPassesBucket() throws Exception {
    SummaryStats empty = new SummaryStats(0, 0, null, null, null, null, null, null);
    when(summaryService.summarize("c", 5L, FROM, TO, "day"))
        .thenReturn(new MetricSummaryResponse(5L, FROM, TO, empty, "day", List.of()));

    mockMvc.perform(get("/metrics/5/summary").header(ApiHeaders.CLIENT_ID, "c")
            .param("from", FROM.toString()).param("to", TO.toString()).param("bucket", "day"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bucket").value("day"))
        .andExpect(jsonPath("$.overall.count").value(0));
  }

  @Test
  void ruleUpdateDefaultsToDryRun() throws Exception {
    when(ruleUpdateService.update(eq("c"), eq(5L), any(UpdateRuleRequest.class), eq(true)))
        .thenReturn(new RuleUpdateResponse(5L, false, DetectionMethod.EWMA, Map.of(), 10, 0, 0,
            false, 0, 0, 0, List.of()));

    mockMvc.perform(put("/metrics/5/rule").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"method\":\"EWMA\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(false));
  }

  @Test
  void ruleUpdateCanBeApplied() throws Exception {
    when(ruleUpdateService.update(eq("c"), eq(5L), any(UpdateRuleRequest.class), eq(false)))
        .thenReturn(new RuleUpdateResponse(5L, true, DetectionMethod.EWMA, Map.of(), 10, 0, 0,
            false, 0, 0, 0, List.of()));

    mockMvc.perform(put("/metrics/5/rule").param("dryRun", "false")
            .header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"method\":\"EWMA\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applied").value(true));
    verify(ruleUpdateService).update(eq("c"), eq(5L), any(UpdateRuleRequest.class), eq(false));
  }

  @Test
  void ruleUpdateWithoutMethodReturns400() throws Exception {
    mockMvc.perform(put("/metrics/5/rule").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(ruleUpdateService);
  }

  @Test
  void missingClientIdReturns401() throws Exception {
    mockMvc.perform(get("/metrics/5/summary")
            .param("from", FROM.toString()).param("to", TO.toString()))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(summaryService);
  }

  @Test
  void nonNumericLimitReturns400() throws Exception {
    mockMvc.perform(get("/metrics/5/anomalies").header(ApiHeaders.CLIENT_ID, "c")
            .param("from", FROM.toString()).param("to", TO.toString()).param("limit", "many"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(anomalyQueryService);
  }
}
