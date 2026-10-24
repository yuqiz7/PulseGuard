package edu.columbia.pulseguard.api;

import org.springframework.http.HttpStatus;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * 409 Conflict: duplicate metric name or out-of-order timestamp.
 */
public class ConflictException extends ApiException {

  /**
   * Creates the error.
   *
   * @param message the error message returned to the client
   */
  public ConflictException(String message) {
    super(HttpStatus.CONFLICT, message);
  }
}
