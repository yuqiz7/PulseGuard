package edu.columbia.pulseguard.controller;

import edu.columbia.pulseguard.api.ApiHeaders;
import edu.columbia.pulseguard.dto.AnomalyQueryResponse;
import edu.columbia.pulseguard.dto.MetricSummaryResponse;
import edu.columbia.pulseguard.dto.RuleUpdateResponse;
import edu.columbia.pulseguard.dto.UpdateRuleRequest;
import edu.columbia.pulseguard.service.AnomalyQueryService;
import edu.columbia.pulseguard.service.MetricSummaryService;
import edu.columbia.pulseguard.service.RuleUpdateService;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// AI-generated: Claude (Anthropic)
/**
 * Endpoints for querying anomalies, summarizing metrics and updating detection rules.
 */
@RestController
@RequestMapping("/metrics/{metricId}")
public class MetricQueryController {

  private final AnomalyQueryService anomalyQueryService;
  private final MetricSummaryService summaryService;
  private final RuleUpdateService ruleUpdateService;

  /**
   * Creates the controller.
   *
   * @param anomalyQueryService queryAnomalies implementation
   * @param summaryService getMetricSummary implementation
   * @param ruleUpdateService updateDetectionRule implementation
   */
  public MetricQueryController(AnomalyQueryService anomalyQueryService,
      MetricSummaryService summaryService, RuleUpdateService ruleUpdateService) {
    this.anomalyQueryService = anomalyQueryService;
    this.summaryService = summaryService;
    this.ruleUpdateService = ruleUpdateService;
  }

  /**
   * queryAnomalies: GET /metrics/{metricId}/anomalies.
   *
   * @param clientId the X-Client-Id header
   * @param metricId the metric ID
   * @param from inclusive start (ISO-8601)
   * @param to inclusive end (ISO-8601)
   * @param minScore minimum anomaly score (default 0)
   * @param limit maximum number of anomalies (default 100, at most 1000)
   * @return matching anomalies and the total count
   */
  @GetMapping("/anomalies")
  public AnomalyQueryResponse queryAnomalies(
      @RequestHeader(ApiHeaders.CLIENT_ID) String clientId,
      @PathVariable("metricId") Long metricId,
      @RequestParam("from") Instant from,
      @RequestParam("to") Instant to,
      @RequestParam(value = "minScore", defaultValue = "0") double minScore,
      @RequestParam(value = "limit", defaultValue = "100") int limit) {
    return anomalyQueryService.query(clientId, metricId, from, to, minScore, limit);
  }

  /**
   * getMetricSummary: GET /metrics/{metricId}/summary.
   *
   * @param clientId the X-Client-Id header
   * @param metricId the metric ID
   * @param from inclusive start (ISO-8601)
   * @param to inclusive end (ISO-8601)
   * @param bucket optional bucket size: hour, 1h, day or 1d
   * @return overall and per-bucket statistics
   */
  @GetMapping("/summary")
  public MetricSummaryResponse getMetricSummary(
      @RequestHeader(ApiHeaders.CLIENT_ID) String clientId,
      @PathVariable("metricId") Long metricId,
      @RequestParam("from") Instant from,
      @RequestParam("to") Instant to,
      @RequestParam(value = "bucket", required = false) String bucket) {
    return summaryService.summarize(clientId, metricId, from, to, bucket);
  }

  /**
   * updateDetectionRule: PUT /metrics/{metricId}/rule.
   *
   * @param clientId the X-Client-Id header
   * @param metricId the metric ID
   * @param dryRun if true (default), only report the effect of the new rule
   * @param request the new rule
   * @return anomaly counts under the old and new rules and the changed points
   */
  @PutMapping("/rule")
  public RuleUpdateResponse updateDetectionRule(
      @RequestHeader(ApiHeaders.CLIENT_ID) String clientId,
      @PathVariable("metricId") Long metricId,
      @RequestParam(value = "dryRun", defaultValue = "true") boolean dryRun,
      @Valid @RequestBody UpdateRuleRequest request) {
    return ruleUpdateService.update(clientId, metricId, request, dryRun);
  }
}
