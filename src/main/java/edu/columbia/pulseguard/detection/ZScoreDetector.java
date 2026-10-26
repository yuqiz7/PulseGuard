
package edu.columbia.pulseguard.detection;

import edu.columbia.pulseguard.model.DetectionMethod;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects anomalies using the mean and population standard deviation
 * of the most recent observations.
 *
 * <p>The incoming point is evaluated before being incorporated into
 * the rolling window.
 */
public final class ZScoreDetector implements AnomalyDetector {

    @Override
    public DetectionResult evaluate(MetricRule rule, DetectorState state, double value) {
        int windowSize = validateRule(rule);
        validateValue(value);
        List<Double> history = state.window();
        int start = Math.max(0, history.size() - windowSize);
        List<Double> window = history.subList(start, history.size());

        if (window.size() < 2) {
            double expected = window.isEmpty() ? value : window.get(0);
            return new DetectionResult(false, 0.0, expected, expected);
        }

        double threshold = rule.param(RuleParams.Z_THRESHOLD, RuleParams.DEFAULT_Z_THRESHOLD);

        double mean = 0.0;
        for (int i = 0; i < window.size(); i++) {
            mean += (window.get(i) - mean) / (i + 1);
        }

        double sumSquaredDifferences = 0.0;
        for (double previous : window) {
            double difference = previous - mean;
            sumSquaredDifferences += difference * difference;
        }

        double variance = sumSquaredDifferences / window.size();
        double stddev = Math.sqrt(variance);
        if (!Double.isFinite(stddev)) {
            throw new IllegalArgumentException("Z-score variance overflow");
        }

        double margin = threshold * stddev;
        double expectedLow = mean - margin;
        double expectedHigh = mean + margin;
        if (!Double.isFinite(expectedLow) || !Double.isFinite(expectedHigh)) {
            throw new IllegalArgumentException("Z-score expected range overflow");
        }

        double difference = Math.abs(value - mean);
        double score;
        boolean exceedsThreshold;

        if (stddev == 0.0) {
            exceedsThreshold = difference > 0.0;
            score = exceedsThreshold ? Double.MAX_VALUE : 0.0;
        } else {
            double rawScore = difference / stddev;
            score = Double.isFinite(rawScore) ? rawScore : Double.MAX_VALUE;
            exceedsThreshold = rawScore > threshold;
        }

        boolean warmedUp = state.sampleCount() >= rule.warmUpSamples();
        boolean anomaly = warmedUp && exceedsThreshold;
        return new DetectionResult(anomaly, score, expectedLow, expectedHigh);
    }

    @Override
    public DetectorState update(MetricRule rule, DetectorState state, double value) {
        int windowSize = validateRule(rule);
        validateValue(value);
        List<Double> newWindow = new ArrayList<>(state.window());
        newWindow.add(value);

        if (newWindow.size() > windowSize) {
            newWindow = new ArrayList<>(newWindow.subList(newWindow.size() - windowSize, newWindow.size()));
        }

        return new DetectorState(
                state.sampleCount() + 1,
                state.ewma(),
                state.ewmaVariance(),
                newWindow
        );
    }

    private int validateRule(MetricRule rule) {
        if (rule.method() != DetectionMethod.ZSCORE) {
            throw new IllegalArgumentException("Expected ZSCORE detection method");
        }

        double windowSize = rule.param(RuleParams.WINDOW_SIZE, RuleParams.DEFAULT_WINDOW_SIZE);
        double threshold = rule.param(RuleParams.Z_THRESHOLD, RuleParams.DEFAULT_Z_THRESHOLD);

        if (!Double.isFinite(windowSize)
                || windowSize < 2
                || windowSize > Integer.MAX_VALUE
                || windowSize != Math.rint(windowSize)) {
            throw new IllegalArgumentException("Window size must be an integer of at least 2");
        }
        if (!Double.isFinite(threshold) || threshold <= 0.0) {
            throw new IllegalArgumentException("Z-score threshold must be positive and finite");
        }
        if (rule.warmUpSamples() < 0) {
            throw new IllegalArgumentException("Warm-up samples cannot be negative");
        }

        return (int) windowSize;
    }

    private void validateValue(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Metric value must be finite");
        }
    }
}
