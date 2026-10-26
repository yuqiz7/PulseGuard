package edu.columbia.pulseguard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import edu.columbia.pulseguard.dto.SummaryStats;
import edu.columbia.pulseguard.model.DataPoint;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

// AI-generated: Claude (Anthropic)
/**
 * Unit tests for {@link SummaryCalculator}.
 */
class SummaryCalculatorTest {

  private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

  private static DataPoint point(long hoursAfterStart, double value, boolean anomaly) {
    return new DataPoint(1L, T0.plusSeconds(hoursAfterStart * 3600), value, anomaly, 0);
  }

  @Test
  void emptyListOnlyHasCounts() {
    SummaryStats stats = SummaryCalculator.summarize(List.of());

    assertThat(stats.count()).isZero();
    assertThat(stats.anomalies()).isZero();
    assertThat(stats.mean()).isNull();
    assertThat(stats.trendPerHour()).isNull();
  }

  @Test
  void computesBasicStatistics() {
    SummaryStats stats = SummaryCalculator.summarize(List.of(
        point(0, 2.0, false), point(1, 4.0, true), point(2, 6.0, false), point(3, 8.0, false)));

    assertThat(stats.count()).isEqualTo(4);
    assertThat(stats.anomalies()).isEqualTo(1);
    assertThat(stats.mean()).isCloseTo(5.0, within(1e-9));
    assertThat(stats.stddev()).isCloseTo(Math.sqrt(5.0), within(1e-9));
    assertThat(stats.min()).isEqualTo(2.0);
    assertThat(stats.max()).isEqualTo(8.0);
    assertThat(stats.anomalyRate()).isCloseTo(0.25, within(1e-9));
  }

  @Test
  void trendIsSlopePerHour() {
    SummaryStats stats = SummaryCalculator.summarize(List.of(
        point(0, 2.0, false), point(1, 4.0, false), point(2, 6.0, false)));

    assertThat(stats.trendPerHour()).isCloseTo(2.0, within(1e-9));
  }

  @Test
  void decreasingSeriesHasNegativeTrend() {
    SummaryStats stats = SummaryCalculator.summarize(List.of(
        point(0, 10.0, false), point(2, 6.0, false)));

    assertThat(stats.trendPerHour()).isCloseTo(-2.0, within(1e-9));
  }

  @Test
  void singlePointHasNoTrend() {
    SummaryStats stats = SummaryCalculator.summarize(List.of(point(0, 3.0, false)));

    assertThat(stats.trendPerHour()).isNull();
    assertThat(stats.stddev()).isZero();
  }
}
