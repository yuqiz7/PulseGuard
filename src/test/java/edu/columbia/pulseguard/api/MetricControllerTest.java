package edu.columbia.pulseguard.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.columbia.pulseguard.model.DetectionMethod;
import edu.columbia.pulseguard.model.Metric;
import edu.columbia.pulseguard.service.MetricRegistrationService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Web-layer tests of {@code POST /metrics}, with the service mocked.
 */
@WebMvcTest(MetricController.class)
class MetricControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private MetricRegistrationService registrationService;

  private static final String VALID_BODY =
      "{\"name\":\"latency\",\"unit\":\"ms\",\"method\":\"ZSCORE\","
          + "\"params\":{\"windowSize\":20},\"warmUpSamples\":5}";

  @Test
  void registerReturns201WithMetric() throws Exception {
    Metric metric = new Metric("client-a", "latency", "ms", DetectionMethod.ZSCORE,
        Map.of("windowSize", 20.0, "zThreshold", 3.0), 5);
    when(registrationService.register(eq("client-a"), any(RegisterMetricRequest.class)))
        .thenReturn(metric);

    mockMvc.perform(post("/metrics").header("X-Client-Id", "client-a")
            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("latency"))
        .andExpect(jsonPath("$.method").value("ZSCORE"))
        .andExpect(jsonPath("$.params.zThreshold").value(3.0))
        .andExpect(jsonPath("$.warmUpSamples").value(5));
    verify(registrationService).register(eq("client-a"), any(RegisterMetricRequest.class));
  }

  @Test
  void missingClientHeaderReturns401() throws Exception {
    mockMvc.perform(post("/metrics").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
    verifyNoInteractions(registrationService);
  }

  @Test
  void blankNameReturns400() throws Exception {
    mockMvc.perform(post("/metrics").header("X-Client-Id", "client-a")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"  \",\"method\":\"EWMA\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            org.hamcrest.Matchers.containsString("name")));
    verifyNoInteractions(registrationService);
  }

  @Test
  void missingMethodAndOverlongNameReturn400() throws Exception {
    mockMvc.perform(post("/metrics").header("X-Client-Id", "client-a")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + "x".repeat(101) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("method"),
                org.hamcrest.Matchers.containsString("name"))));
  }

  @Test
  void unknownMethodAndMalformedJsonReturn400() throws Exception {
    mockMvc.perform(post("/metrics").header("X-Client-Id", "client-a")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"a\",\"method\":\"MEDIAN\"}"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/metrics").header("X-Client-Id", "client-a")
            .contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
  }

  @Test
  void serviceErrorsMapToTheirStatus() throws Exception {
    when(registrationService.register(eq("client-a"), any(RegisterMetricRequest.class)))
        .thenThrow(new ConflictException("Metric 'latency' already exists for this client"));

    mockMvc.perform(post("/metrics").header("X-Client-Id", "client-a")
            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Conflict"));
  }

  @Test
  void ruleErrorsFromTheServiceReturn400() throws Exception {
    when(registrationService.register(eq("client-a"), any(RegisterMetricRequest.class)))
        .thenThrow(new BadRequestException("alpha must be between 0 and 1 (exclusive)"));

    mockMvc.perform(post("/metrics").header("X-Client-Id", "client-a")
            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("alpha must be between 0 and 1 (exclusive)"));
  }
}
