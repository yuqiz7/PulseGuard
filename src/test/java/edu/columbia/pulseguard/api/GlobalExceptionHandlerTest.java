package edu.columbia.pulseguard.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// AI-generated: Claude (Anthropic)
/**
 * Verifies the status codes and error bodies produced by {@link GlobalExceptionHandler},
 * using a small test-only controller.
 */
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void apiExceptionUsesItsStatus() throws Exception {
    mockMvc.perform(get("/probe/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.message").value("Metric 7 not found"));
  }

  @Test
  void conflictExceptionReturns409() throws Exception {
    mockMvc.perform(get("/probe/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("duplicate"));
  }

  @Test
  void badRequestExceptionReturns400() throws Exception {
    mockMvc.perform(get("/probe/bad-request"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("bad input"));
  }

  @Test
  void missingClientIdReturns401() throws Exception {
    mockMvc.perform(get("/probe/items/1").param("limit", "5"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Missing X-Client-Id header"));
  }

  @Test
  void missingOtherHeaderReturns400() throws Exception {
    mockMvc.perform(get("/probe/other-header"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Missing X-Other header"));
  }

  @Test
  void validRequestSucceeds() throws Exception {
    mockMvc.perform(get("/probe/items/1").param("limit", "5").header(ApiHeaders.CLIENT_ID, "c"))
        .andExpect(status().isOk());
  }

  @Test
  void missingQueryParameterReturns400() throws Exception {
    mockMvc.perform(get("/probe/items/1").header(ApiHeaders.CLIENT_ID, "c"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Missing query parameter: limit"));
  }

  @Test
  void wronglyTypedPathVariableReturns400() throws Exception {
    mockMvc.perform(get("/probe/items/abc").param("limit", "5").header(ApiHeaders.CLIENT_ID, "c"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid value for parameter: id"));
  }

  @Test
  void invalidBodyFieldReturns400() throws Exception {
    mockMvc.perform(post("/probe/items")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("name:")));
  }

  @Test
  void malformedJsonReturns400() throws Exception {
    mockMvc.perform(post("/probe/items")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
  }

  /** Request body used to trigger bean validation. */
  record ProbeBody(@NotBlank String name) {
  }

  /** Test-only controller whose endpoints raise each kind of error. */
  @RestController
  static class ProbeController {

    @GetMapping("/probe/not-found")
    String notFound() {
      throw new NotFoundException("Metric 7 not found");
    }

    @GetMapping("/probe/conflict")
    String conflict() {
      throw new ConflictException("duplicate");
    }

    @GetMapping("/probe/bad-request")
    String badRequest() {
      throw new BadRequestException("bad input");
    }

    @GetMapping("/probe/other-header")
    String otherHeader(@RequestHeader("X-Other") String other) {
      return other;
    }

    @GetMapping("/probe/items/{id}")
    String item(@RequestHeader(ApiHeaders.CLIENT_ID) String clientId,
        @PathVariable("id") Long id, @RequestParam("limit") int limit) {
      return clientId + id + limit;
    }

    @PostMapping("/probe/items")
    String create(@Valid @RequestBody ProbeBody body) {
      return body.name();
    }
  }
}
