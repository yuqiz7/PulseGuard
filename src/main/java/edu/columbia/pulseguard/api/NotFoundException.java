package edu.columbia.pulseguard.api;

import org.springframework.http.HttpStatus;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * 404 Not Found: the metric does not exist or belongs to another client.
 */
public class NotFoundException extends ApiException {

  /**
   * Creates the error.
   *
   * @param message the error message returned to the client
   */
  public NotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, message);
  }
}
