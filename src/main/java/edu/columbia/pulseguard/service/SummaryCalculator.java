package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.dto.SummaryStats;
import edu.columbia.pulseguard.model.DataPoint;
import java.time.Instant;
import java.util.List;

// AI-generated: Claude (Anthropic)
/**
 * Computes summary statistics (count, mean, spread, extremes, anomaly rate, trend) of points.
 */
public final class SummaryCalculator {

  private static final double SECONDS_PER_HOUR = 3600.0;

  private SummaryCalculator() {
  }

  /**
   * Summarizes a list of points.
   *
   * @param points the points, oldest first
   * @return the statistics; only the counts are set when the list is empty
   */
  public static SummaryStats summarize(List<DataPoint> points) {
    if (points.isEmpty()) {
      return new SummaryStats(0, 0, null, null, null, null, null, null);
    }
    long count = points.size();
    long anomalies = points.stream().filter(DataPoint::isAnomaly).count();
    double sum = 0;
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (DataPoint point : points) {
      sum += point.getValue();
      min = Math.min(min, point.getValue());
      max = Math.max(max, point.getValue());
    }
    double mean = sum / count;
    double squaredDeviations = 0;
    for (DataPoint point : points) {
      squaredDeviations += (point.getValue() - mean) * (point.getValue() - mean);
    }
    double stddev = Math.sqrt(squaredDeviations / count);
    return new SummaryStats(count, anomalies, mean, stddev, min, max,
        (double) anomalies / count, trendPerHour(points, mean));
  }

  /**
   * Least-squares slope of value against time in hours, or null if all timestamps are equal.
   */
  private static Double trendPerHour(List<DataPoint> points, double meanValue) {
    Instant origin = points.get(0).getTs();
    double meanHours = 0;
    for (DataPoint point : points) {
      meanHours += hoursSince(origin, point.getTs());
    }
    meanHours /= points.size();
    double covariance = 0;
    double timeVariance = 0;
    for (DataPoint point : points) {
      double hours = hoursSince(origin, point.getTs()) - meanHours;
      covariance += hours * (point.getValue() - meanValue);
      timeVariance += hours * hours;
    }
    if (timeVariance == 0) {
      return null;
    }
    return covariance / timeVariance;
  }

  private static double hoursSince(Instant origin, Instant ts) {
    return (ts.toEpochMilli() - origin.toEpochMilli()) / 1000.0 / SECONDS_PER_HOUR;
  }
}
