package edu.columbia.pulseguard.api;

import org.springframework.http.HttpStatus;

// AI-generated: Claude (Anthropic)
/**
 * JSON body returned for every error.
 *
 * @param status the HTTP status code, e.g. 404
 * @param error the HTTP reason phrase, e.g. "Not Found"
 * @param message a human-readable explanation
 */
public record ErrorResponse(int status, String error, String message) {

  /**
   * Builds an error body for a status.
   *
   * @param status the HTTP status
   * @param message the explanation
   * @return the error body
   */
  public static ErrorResponse of(HttpStatus status, String message) {
    return new ErrorResponse(status.value(), status.getReasonPhrase(), message);
  }
}
