package edu.columbia.pulseguard.api;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// AI-assisted: Claude (Anthropic), generated, reviewed by Yuqi Zhang
/**
 * Converts exceptions into {@link ErrorResponse} bodies with the documented status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Handles errors thrown by services (400, 401, 404, 409).
   *
   * @param ex the error
   * @return the error response with the exception's status
   */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
    return build(ex.getStatus(), ex.getMessage());
  }

  /**
   * Returns 401 when X-Client-Id is missing, 400 for any other missing header.
   *
   * @param ex the error
   * @return the error response
   */
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
    if (ApiHeaders.CLIENT_ID.equalsIgnoreCase(ex.getHeaderName())) {
      return build(HttpStatus.UNAUTHORIZED, "Missing " + ApiHeaders.CLIENT_ID + " header");
    }
    return build(HttpStatus.BAD_REQUEST, "Missing " + ex.getHeaderName() + " header");
  }

  /**
   * Returns 400 listing the request body fields that failed validation.
   *
   * @param ex the error
   * @return the error response
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
    String details = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .sorted()
        .collect(Collectors.joining("; "));
    return build(HttpStatus.BAD_REQUEST, "Invalid request body: " + details);
  }

  /**
   * Returns 400 when the request body is not valid JSON or has wrongly typed fields.
   *
   * @param ex the error
   * @return the error response
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return build(HttpStatus.BAD_REQUEST, "Malformed JSON request body");
  }

  /**
   * Returns 400 when a required query parameter is missing.
   *
   * @param ex the error
   * @return the error response
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException ex) {
    return build(HttpStatus.BAD_REQUEST, "Missing query parameter: " + ex.getParameterName());
  }

  /**
   * Returns 400 when a path or query parameter has the wrong type (e.g. a non-numeric ID).
   *
   * @param ex the error
   * @return the error response
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + ex.getName());
  }

  private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
  }
}
