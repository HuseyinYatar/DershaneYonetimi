package com.dershane.userservice.shared.domain;

import static com.dershane.userservice.shared.domain.ErrorCategory.BAD_GATEWAY;
import static com.dershane.userservice.shared.domain.ErrorCategory.CONFLICT;
import static com.dershane.userservice.shared.domain.ErrorCategory.FORBIDDEN;
import static com.dershane.userservice.shared.domain.ErrorCategory.INTERNAL;
import static com.dershane.userservice.shared.domain.ErrorCategory.NOT_FOUND;
import static com.dershane.userservice.shared.domain.ErrorCategory.RULE_VIOLATION;
import static com.dershane.userservice.shared.domain.ErrorCategory.UNAUTHENTICATED;
import static com.dershane.userservice.shared.domain.ErrorCategory.UNAVAILABLE;
import static com.dershane.userservice.shared.domain.ErrorCategory.UNSUPPORTED_METHOD;
import static com.dershane.userservice.shared.domain.ErrorCategory.VALIDATION;

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

  RESOURCE_NOT_FOUND(NOT_FOUND),
  BRANCH_NOT_FOUND(NOT_FOUND),
  SUBJECT_NOT_FOUND(NOT_FOUND),
  CLASSROOM_NOT_FOUND(NOT_FOUND),
  USER_NOT_FOUND(NOT_FOUND),
  STUDENT_NOT_FOUND(NOT_FOUND),
  TEACHER_NOT_FOUND(NOT_FOUND),
  GUARDIAN_NOT_FOUND(NOT_FOUND),
  ADMIN_NOT_FOUND(NOT_FOUND),
  TEACHER_ASSIGNMENT_NOT_FOUND(NOT_FOUND),
  ACTIVE_MEMBERSHIP_NOT_FOUND(NOT_FOUND),
  STUDENT_GUARDIAN_NOT_FOUND(NOT_FOUND),

  METHOD_NOT_ALLOWED(UNSUPPORTED_METHOD),

  BRANCH_CODE_TAKEN(CONFLICT),
  SUBJECT_CODE_TAKEN(CONFLICT),
  CLASSROOM_NAME_TAKEN(CONFLICT),
  EMAIL_TAKEN(CONFLICT),
  USERNAME_TAKEN(CONFLICT),
  STUDENT_NUMBER_TAKEN(CONFLICT),
  TEACHER_ASSIGNMENT_EXISTS(CONFLICT),
  BRANCH_HAS_ACTIVE_CLASSROOMS(CONFLICT),
  CLASSROOM_HAS_ACTIVE_STUDENTS(CONFLICT),
  CONCURRENT_MODIFICATION(CONFLICT),
  DATA_CONFLICT(CONFLICT),
  IDEMPOTENCY_REQUEST_IN_PROGRESS(CONFLICT),

  BRANCH_INACTIVE(RULE_VIOLATION),
  SUBJECT_INACTIVE(RULE_VIOLATION),
  CLASSROOM_INACTIVE(RULE_VIOLATION),
  USER_INACTIVE(RULE_VIOLATION),
  BRANCH_MISMATCH(RULE_VIOLATION),
  GRADE_LEVEL_MISMATCH(RULE_VIOLATION),
  EMAIL_REQUIRED(RULE_VIOLATION),
  PHONE_REQUIRED(RULE_VIOLATION),
  EMAIL_NOT_AVAILABLE(RULE_VIOLATION),
  TEMPORARY_PASSWORD_NOT_ALLOWED(RULE_VIOLATION),
  CANNOT_DEACTIVATE_SELF(RULE_VIOLATION),
  IDEMPOTENCY_KEY_REUSED(RULE_VIOLATION),

  IDENTITY_PROVIDER_ERROR(BAD_GATEWAY),
  IDENTITY_PROVIDER_UNAVAILABLE(UNAVAILABLE),

  INTERNAL_ERROR(INTERNAL);

  private final ErrorCategory category;

  ErrorCode(ErrorCategory category) {
    this.category = category;
  }

  public String messageKey() {
    return "error." + name();
  }
}
