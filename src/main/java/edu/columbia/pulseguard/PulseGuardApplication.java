package edu.columbia.pulseguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Entry point of the PulseGuard time-series anomaly detection service.
 */
@SpringBootApplication
public class PulseGuardApplication {

  /**
   * Starts the Spring Boot application.
   *
   * @param args command-line arguments passed to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(PulseGuardApplication.class, args);
  }
}
