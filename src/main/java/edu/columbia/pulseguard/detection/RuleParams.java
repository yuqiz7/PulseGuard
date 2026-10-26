package edu.columbia.pulseguard.detection;

// AI-generated: Claude (Anthropic)
/**
 * Parameter keys and default values for detection rules, shared by all areas.
 */
public final class RuleParams {

  /** ZSCORE: number of most recent points used for mean and standard deviation. */
  public static final String WINDOW_SIZE = "windowSize";
  /** ZSCORE: a point is anomalous if |value - mean| / stddev exceeds this. */
  public static final String Z_THRESHOLD = "zThreshold";
  /** EWMA: smoothing factor, 0 &lt; alpha &lt; 1. */
  public static final String ALPHA = "alpha";
  /** EWMA: a point is anomalous if |value - ewma| / ewmaStddev exceeds this. */
  public static final String TOLERANCE = "tolerance";
  /** THRESHOLD: values below this bound are anomalous. */
  public static final String LOWER_BOUND = "lowerBound";
  /** THRESHOLD: values above this bound are anomalous. */
  public static final String UPPER_BOUND = "upperBound";

  /** Default z-score window size. */
  public static final double DEFAULT_WINDOW_SIZE = 30;
  /** Default z-score threshold. */
  public static final double DEFAULT_Z_THRESHOLD = 3.0;
  /** Default EWMA smoothing factor. */
  public static final double DEFAULT_ALPHA = 0.3;
  /** Default EWMA tolerance in standard deviations. */
  public static final double DEFAULT_TOLERANCE = 3.0;
  /** Default warm-up sample count when a client does not specify one. */
  public static final int DEFAULT_WARM_UP_SAMPLES = 10;

  private RuleParams() {
  }
}
