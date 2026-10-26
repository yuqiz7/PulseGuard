package edu.columbia.pulseguard.api;

import org.springframework.http.HttpStatus;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Base class of errors that map to a specific HTTP status; handled by
 * {@link GlobalExceptionHandler}.
 */
public abstract class ApiException extends RuntimeException {

  private final HttpStatus status;

  /**
   * Creates an API error.
   *
   * @param status the HTTP status returned to the client
   * @param message the error message returned to the client
   */
  protected ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
