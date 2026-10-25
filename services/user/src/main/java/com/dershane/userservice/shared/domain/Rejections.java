package com.dershane.userservice.shared.domain;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Builds a rejection and logs it in the same step, so no guard clause can fail silently.
 *
 * <p>Usage: {@code throw Rejections.reject(log, ErrorCode.X, "Why it was rejected", "id", id);}
 * Context values must be identifiers only; personal data (names, e-mail, phone, student number)
 * never goes into logs.
 */
public final class Rejections {

  private Rejections() {}

  public static DomainException reject(
      Logger log, ErrorCode errorCode, String message, Object... keyValues) {
    LoggingEventBuilder event = log.atWarn().addKeyValue("errorCode", errorCode.name());
    addKeyValues(event, keyValues).log(message);
    return new DomainException(errorCode);
  }

  /**
   * For failures of an external dependency: logged at ERROR. Pass {@code cause = null} when the
   * exception message may contain personal data (e.g. an HTTP response body).
   */
  public static DomainException fail(
      Logger log, ErrorCode errorCode, String message, Throwable cause, Object... keyValues) {
    LoggingEventBuilder event = log.atError().addKeyValue("errorCode", errorCode.name());
    if (cause != null) {
      event = event.setCause(cause);
    }
    addKeyValues(event, keyValues).log(message);
    return cause == null ? new DomainException(errorCode) : new DomainException(errorCode, cause);
  }

  private static LoggingEventBuilder addKeyValues(LoggingEventBuilder event, Object... keyValues) {
    LoggingEventBuilder result = event;
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      result = result.addKeyValue(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    if (keyValues.length % 2 != 0) {
      result = result.addKeyValue("unpairedValue", keyValues[keyValues.length - 1]);
    }
    return result;
  }
}
