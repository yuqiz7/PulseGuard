package edu.columbia.pulseguard.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.columbia.pulseguard.service.DataIngestionService;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// AI-assisted: Claude (Anthropic), generated, reviewed by Sidharth Sharma
/**
 * Web-layer tests of {@code POST /metrics/{metricId}/datapoints}, with the service mocked.
 */
@WebMvcTest(DataPointController.class)
class DataPointControllerTest {

  private static final String ONE_POINT =
      "{\"dataPoints\":[{\"timestamp\":\"2026-10-20T12:00:00Z\",\"value\":42.5}]}";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private DataIngestionService ingestionService;

  private static MockHttpServletRequestBuilder submit(String path, String body) {
    return post(path).header("X-Client-Id", "client-a")
        .contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static String batchOf(int size) {
    return "{\"dataPoints\":[" + IntStream.range(0, size)
        .mapToObj(i -> "{\"timestamp\":\"2026-10-20T12:00:00Z\",\"value\":1}")
        .collect(Collectors.joining(",")) + "]}";
  }

  @Test
  void submitReturns200WithVerdicts() throws Exception {
    Instant ts = Instant.parse("2026-10-20T12:00:00Z");
    when(ingestionService.submit(eq("client-a"), eq(7L), any(SubmitDataPointsRequest.class)))
        .thenReturn(new SubmitDataPointsResponse(1, 1,
            List.of(new PointVerdict(ts, 42.5, true, 4.2, 0, 10))));

    mockMvc.perform(submit("/metrics/7/datapoints", ONE_POINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(1))
        .andExpect(jsonPath("$.anomalies").value(1))
        .andExpect(jsonPath("$.results[0].timestamp").value("2026-10-20T12:00:00Z"))
        .andExpect(jsonPath("$.results[0].anomaly").value(true))
        .andExpect(jsonPath("$.results[0].expectedHigh").value(10.0));
    verify(ingestionService).submit(eq("client-a"), eq(7L), any(SubmitDataPointsRequest.class));
  }

  @Test
  void batchSizeBoundariesAre1And1000() throws Exception {
    when(ingestionService.submit(any(), any(), any()))
        .thenReturn(new SubmitDataPointsResponse(0, 0, List.of()));

    mockMvc.perform(submit("/metrics/7/datapoints", batchOf(1))).andExpect(status().isOk());
    mockMvc.perform(submit("/metrics/7/datapoints", batchOf(1000))).andExpect(status().isOk());
    mockMvc.perform(submit("/metrics/7/datapoints", batchOf(1001)))
        .andExpect(status().isBadRequest());
    mockMvc.perform(submit("/metrics/7/datapoints", "{\"dataPoints\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidBodiesReturn400WithoutCallingTheService() throws Exception {
    String[] bodies = {
        "{}",
        "{\"dataPoints\":null}",
        "{\"dataPoints\":[null]}",
        "{\"dataPoints\":[{\"value\":1}]}",
        "{\"dataPoints\":[{\"timestamp\":\"2026-10-20T12:00:00Z\"}]}",
        "{\"dataPoints\":[{\"timestamp\":\"yesterday\",\"value\":1}]}",
        "{\"dataPoints\":[{\"timestamp\":\"2026-10-20T12:00:00Z\",\"value\":\"abc\"}]}",
        "not json"
    };
    for (String body : bodies) {
      mockMvc.perform(submit("/metrics/7/datapoints", body)).andExpect(status().isBadRequest());
    }
    verifyNoInteractions(ingestionService);
  }

  @Test
  void missingClientHeaderReturns401() throws Exception {
    mockMvc.perform(post("/metrics/7/datapoints").contentType(MediaType.APPLICATION_JSON)
            .content(ONE_POINT))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(ingestionService);
  }

  @Test
  void nonNumericMetricIdReturns400() throws Exception {
    mockMvc.perform(submit("/metrics/abc/datapoints", ONE_POINT))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid value for parameter: metricId"));
  }

  @Test
  void serviceErrorsMapToTheirStatus() throws Exception {
    when(ingestionService.submit(any(), eq(404L), any()))
        .thenThrow(new NotFoundException("Metric 404 not found"));
    when(ingestionService.submit(any(), eq(409L), any()))
        .thenThrow(new ConflictException("Timestamp is not later than the last point"));

    mockMvc.perform(submit("/metrics/404/datapoints", ONE_POINT))
        .andExpect(status().isNotFound());
    mockMvc.perform(submit("/metrics/409/datapoints", ONE_POINT))
        .andExpect(status().isConflict());
  }
}
