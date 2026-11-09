package com.dershane.taskservice.shared.domain;

import static com.dershane.taskservice.shared.domain.ErrorCategory.CONFLICT;
import static com.dershane.taskservice.shared.domain.ErrorCategory.FORBIDDEN;
import static com.dershane.taskservice.shared.domain.ErrorCategory.INTERNAL;
import static com.dershane.taskservice.shared.domain.ErrorCategory.NOT_FOUND;
import static com.dershane.taskservice.shared.domain.ErrorCategory.RULE_VIOLATION;
import static com.dershane.taskservice.shared.domain.ErrorCategory.UNAUTHENTICATED;
import static com.dershane.taskservice.shared.domain.ErrorCategory.UNSUPPORTED_METHOD;
import static com.dershane.taskservice.shared.domain.ErrorCategory.VALIDATION;

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
  NOT_TASK_TEACHER(FORBIDDEN),
  GUARDIAN_NOT_LINKED(FORBIDDEN),

  RESOURCE_NOT_FOUND(NOT_FOUND),
  TASK_NOT_FOUND(NOT_FOUND),
  ASSIGNMENT_NOT_FOUND(NOT_FOUND),
  MEMBERSHIP_NOT_FOUND(NOT_FOUND),
  GUARDIAN_LINK_NOT_FOUND(NOT_FOUND),

  METHOD_NOT_ALLOWED(UNSUPPORTED_METHOD),

  ASSIGNMENT_ALREADY_GRADED(CONFLICT),
  CONCURRENT_MODIFICATION(CONFLICT),
  DATA_CONFLICT(CONFLICT),
  IDEMPOTENCY_REQUEST_IN_PROGRESS(CONFLICT),

  INVALID_DATE_RANGE(RULE_VIOLATION),
  START_DATE_IN_PAST(RULE_VIOLATION),
  DUE_IN_PAST(RULE_VIOLATION),
  INVALID_RECURRENCE(RULE_VIOLATION),
  TARGET_NOT_IN_BRANCH(RULE_VIOLATION),
  TOO_MANY_ATTACHMENTS(RULE_VIOLATION),
  TASK_INACTIVE(RULE_VIOLATION),
  ASSIGNMENT_CANCELLED(RULE_VIOLATION),
  EMPTY_SUBMISSION(RULE_VIOLATION),
  EMPTY_EVALUATION(RULE_VIOLATION),
  NOT_SUBMITTED(RULE_VIOLATION),
  INVALID_SCORE(RULE_VIOLATION),
  STUDENT_FILTER_REQUIRED(RULE_VIOLATION),
  IDEMPOTENCY_KEY_REUSED(RULE_VIOLATION),

  INTERNAL_ERROR(INTERNAL);

  private final ErrorCategory category;

  ErrorCode(ErrorCategory category) {
    this.category = category;
  }

  public String messageKey() {
    return "error." + name();
  }
}
