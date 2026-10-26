package edu.columbia.pulseguard.api;

import org.springframework.http.HttpStatus;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * 401 Unauthorized: the X-Client-Id header is missing or blank.
 */
public class UnauthorizedException extends ApiException {

  /**
   * Creates the error.
   *
   * @param message the error message returned to the client
   */
  public UnauthorizedException(String message) {
    super(HttpStatus.UNAUTHORIZED, message);
  }
}
