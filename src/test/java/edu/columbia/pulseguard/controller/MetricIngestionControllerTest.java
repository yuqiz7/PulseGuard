package edu.columbia.pulseguard.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.columbia.pulseguard.api.ApiHeaders;
import edu.columbia.pulseguard.api.ConflictException;
import edu.columbia.pulseguard.api.GlobalExceptionHandler;
import edu.columbia.pulseguard.dto.PointResult;
import edu.columbia.pulseguard.dto.RegisterMetricRequest;
import edu.columbia.pulseguard.dto.SubmitDataPointsResponse;
import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.service.DataIngestionService;
import edu.columbia.pulseguard.service.MetricRegistrationService;
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
 * Web-layer tests for {@link MetricIngestionController}, with the services mocked.
 */
@ExtendWith(MockitoExtension.class)
class MetricIngestionControllerTest {

  @Mock
  private MetricRegistrationService registrationService;

  @Mock
  private DataIngestionService ingestionService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .standaloneSetup(new MetricIngestionController(registrationService, ingestionService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void registerReturns201WithMetric() throws Exception {
    Metric metric = new Metric("c", "cpu", "%", DetectionMethod.ZSCORE,
        Map.of("windowSize", 30.0), 10);
    when(registrationService.register(eq("c"), any(RegisterMetricRequest.class)))
        .thenReturn(metric);

    mockMvc.perform(post("/metrics").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"cpu\",\"unit\":\"%\",\"method\":\"ZSCORE\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("cpu"))
        .andExpect(jsonPath("$.method").value("ZSCORE"))
        .andExpect(jsonPath("$.params.windowSize").value(30.0))
        .andExpect(jsonPath("$.warmUpSamples").value(10));
  }

  @Test
  void registerWithoutNameReturns400() throws Exception {
    mockMvc.perform(post("/metrics").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"method\":\"ZSCORE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("name")));
    verifyNoInteractions(registrationService);
  }

  @Test
  void registerWithUnknownMethodReturns400() throws Exception {
    mockMvc.perform(post("/metrics").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"cpu\",\"method\":\"MAGIC\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerWithoutClientIdReturns401() throws Exception {
    mockMvc.perform(post("/metrics")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"cpu\",\"method\":\"ZSCORE\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void duplicateRegistrationReturns409() throws Exception {
    when(registrationService.register(eq("c"), any(RegisterMetricRequest.class)))
        .thenThrow(new ConflictException("Metric 'cpu' already exists for this client"));

    mockMvc.perform(post("/metrics").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"cpu\",\"method\":\"ZSCORE\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void submitReturnsVerdicts() throws Exception {
    Instant ts = Instant.parse("2026-10-01T00:00:00Z");
    when(ingestionService.submit(eq("c"), eq(3L), anyList()))
        .thenReturn(new SubmitDataPointsResponse(3L, 1, 1,
            List.of(new PointResult(ts, 99.0, true, 4.2, 1.0, 9.0))));

    mockMvc.perform(post("/metrics/3/datapoints").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"points\":[{\"timestamp\":\"2026-10-01T00:00:00Z\",\"value\":99}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.anomalies").value(1))
        .andExpect(jsonPath("$.results[0].anomaly").value(true));
  }

  @Test
  void submitWithEmptyBatchReturns400() throws Exception {
    mockMvc.perform(post("/metrics/3/datapoints").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"points\":[]}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(ingestionService);
  }

  @Test
  void submitWithMissingValueReturns400() throws Exception {
    mockMvc.perform(post("/metrics/3/datapoints").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"points\":[{\"timestamp\":\"2026-10-01T00:00:00Z\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("value")));
  }

  @Test
  void submitWithBadTimestampReturns400() throws Exception {
    mockMvc.perform(post("/metrics/3/datapoints").header(ApiHeaders.CLIENT_ID, "c")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"points\":[{\"timestamp\":\"yesterday\",\"value\":1}]}"))
        .andExpect(status().isBadRequest());
  }
}
