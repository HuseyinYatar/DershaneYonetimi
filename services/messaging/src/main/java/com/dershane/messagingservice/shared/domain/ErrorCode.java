package com.dershane.messagingservice.shared.domain;

import static com.dershane.messagingservice.shared.domain.ErrorCategory.CONFLICT;
import static com.dershane.messagingservice.shared.domain.ErrorCategory.FORBIDDEN;
import static com.dershane.messagingservice.shared.domain.ErrorCategory.INTERNAL;
import static com.dershane.messagingservice.shared.domain.ErrorCategory.NOT_FOUND;
import static com.dershane.messagingservice.shared.domain.ErrorCategory.RULE_VIOLATION;
import static com.dershane.messagingservice.shared.domain.ErrorCategory.UNAUTHENTICATED;
import static com.dershane.messagingservice.shared.domain.ErrorCategory.UNAVAILABLE;
import static com.dershane.messagingservice.shared.domain.ErrorCategory.UNSUPPORTED_METHOD;
import static com.dershane.messagingservice.shared.domain.ErrorCategory.VALIDATION;

import lombok.Getter;

/**
 * Stable, English error codes returned to clients as {@code errorCode}. The human-readable text
 * lives in {@code messages*.properties} under {@code error.<CODE>}.
 */
@Getter
public enum ErrorCode {
  VALIDATION_FAILED(VALIDATION),
  MALFORMED_REQUEST(VALIDATION),

  AUTHENTICATION_REQUIRED(UNAUTHENTICATED),

  ACCESS_DENIED(FORBIDDEN),
  BRANCH_SCOPE_VIOLATION(FORBIDDEN),
  CONVERSATION_NOT_ALLOWED(FORBIDDEN),
  CONVERSATION_ACCESS_DENIED(FORBIDDEN),
  NOT_MESSAGE_SENDER(FORBIDDEN),
  ACCOUNT_INACTIVE(FORBIDDEN),

  RESOURCE_NOT_FOUND(NOT_FOUND),
  USER_NOT_FOUND(NOT_FOUND),
  CONVERSATION_NOT_FOUND(NOT_FOUND),
  MESSAGE_NOT_FOUND(NOT_FOUND),
  REPORT_NOT_FOUND(NOT_FOUND),

  METHOD_NOT_ALLOWED(UNSUPPORTED_METHOD),

  CONCURRENT_MODIFICATION(CONFLICT),
  DATA_CONFLICT(CONFLICT),
  IDEMPOTENCY_REQUEST_IN_PROGRESS(CONFLICT),
  REPORT_ALREADY_EXISTS(CONFLICT),
  REPORT_ALREADY_CLOSED(CONFLICT),

  PARTICIPANT_INACTIVE(RULE_VIOLATION),
  EMPTY_MESSAGE(RULE_VIOLATION),
  PHOTO_REQUIRED(RULE_VIOLATION),
  INVALID_MESSAGE_BODY(RULE_VIOLATION),
  MESSAGE_DELETED(RULE_VIOLATION),
  EDIT_WINDOW_EXPIRED(RULE_VIOLATION),
  READ_SEQ_OUT_OF_RANGE(RULE_VIOLATION),
  OWN_MESSAGE_REPORT(RULE_VIOLATION),
  IDEMPOTENCY_KEY_REUSED(RULE_VIOLATION),

  REALTIME_UNAVAILABLE(UNAVAILABLE),

  INTERNAL_ERROR(INTERNAL);

  private final ErrorCategory category;

  ErrorCode(ErrorCategory category) {
    this.category = category;
  }

  public String messageKey() {
    return "error." + name();
  }
}
