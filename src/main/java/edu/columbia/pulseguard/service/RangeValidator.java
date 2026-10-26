package edu.columbia.pulseguard.service;

import edu.columbia.pulseguard.api.BadRequestException;
import java.time.Instant;

// AI-generated: Claude (Anthropic)
/**
 * Validation of from/to query ranges shared by the query endpoints.
 */
final class RangeValidator {

  private RangeValidator() {
  }

  /**
   * Checks that from is not after to.
   *
   * @param from inclusive start of the range
   * @param to inclusive end of the range
   * @throws BadRequestException if from is after to
   */
  static void requireValidRange(Instant from, Instant to) {
    if (from.isAfter(to)) {
      throw new BadRequestException("from must not be after to");
    }
  }
}
