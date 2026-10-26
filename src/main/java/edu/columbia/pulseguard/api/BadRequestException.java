package edu.columbia.pulseguard.api;

import org.springframework.http.HttpStatus;

// AI-generated: Claude (Anthropic)
/**
 * 400 Bad Request: the request parameters or body are invalid.
 */
public class BadRequestException extends ApiException {

  /**
   * Creates the error.
   *
   * @param message the error message returned to the client
   */
  public BadRequestException(String message) {
    super(HttpStatus.BAD_REQUEST, message);
  }
}
