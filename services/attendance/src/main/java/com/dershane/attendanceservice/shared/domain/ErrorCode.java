package com.dershane.attendanceservice.shared.domain;

import static com.dershane.attendanceservice.shared.domain.ErrorCategory.CONFLICT;
import static com.dershane.attendanceservice.shared.domain.ErrorCategory.FORBIDDEN;
import static com.dershane.attendanceservice.shared.domain.ErrorCategory.INTERNAL;
import static com.dershane.attendanceservice.shared.domain.ErrorCategory.NOT_FOUND;
import static com.dershane.attendanceservice.shared.domain.ErrorCategory.RULE_VIOLATION;
import static com.dershane.attendanceservice.shared.domain.ErrorCategory.UNAUTHENTICATED;
import static com.dershane.attendanceservice.shared.domain.ErrorCategory.UNSUPPORTED_METHOD;
import static com.dershane.attendanceservice.shared.domain.ErrorCategory.VALIDATION;

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
  NOT_SESSION_TEACHER(FORBIDDEN),
  GUARDIAN_NOT_LINKED(FORBIDDEN),

  RESOURCE_NOT_FOUND(NOT_FOUND),
  SCHEDULE_NOT_FOUND(NOT_FOUND),
  SESSION_NOT_FOUND(NOT_FOUND),
  EXCUSE_NOT_FOUND(NOT_FOUND),
  MEMBERSHIP_NOT_FOUND(NOT_FOUND),
  GUARDIAN_LINK_NOT_FOUND(NOT_FOUND),

  METHOD_NOT_ALLOWED(UNSUPPORTED_METHOD),

  SESSION_CLASSROOM_OVERLAP(CONFLICT),
  SESSION_TEACHER_BUSY(CONFLICT),
  ATTENDANCE_ALREADY_TAKEN(CONFLICT),
  EXCUSE_ALREADY_DECIDED(CONFLICT),
  CONCURRENT_MODIFICATION(CONFLICT),
  DATA_CONFLICT(CONFLICT),
  IDEMPOTENCY_REQUEST_IN_PROGRESS(CONFLICT),

  BRANCH_MISMATCH(RULE_VIOLATION),
  INVALID_TIME_RANGE(RULE_VIOLATION),
  INVALID_DATE_RANGE(RULE_VIOLATION),
  SESSION_NOT_PLANNED(RULE_VIOLATION),
  SESSION_NOT_COMPLETED(RULE_VIOLATION),
  SESSION_CANCELLED(RULE_VIOLATION),
  SESSION_EXPIRED(RULE_VIOLATION),
  ATTENDANCE_TOO_EARLY(RULE_VIOLATION),
  ROSTER_MISMATCH(RULE_VIOLATION),
  REASON_REQUIRED(RULE_VIOLATION),
  CORRECTION_WINDOW_CLOSED(RULE_VIOLATION),
  STUDENT_NOT_IN_ROSTER(RULE_VIOLATION),
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
