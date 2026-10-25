package com.dershane.userservice.shared.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps database constraint names (see {@code V1__initial_schema.sql}) to error codes. Services
 * check these rules before writing; the constraints catch concurrent requests that pass the check
 * at the same time.
 */
public final class ConstraintErrorCodes {

  private static final Map<String, ErrorCode> CODES =
      Map.ofEntries(
          Map.entry("uk_branch_code", ErrorCode.BRANCH_CODE_TAKEN),
          Map.entry("uk_subject_code", ErrorCode.SUBJECT_CODE_TAKEN),
          Map.entry("uk_classroom_branch_term_name", ErrorCode.CLASSROOM_NAME_TAKEN),
          Map.entry("uk_users_username", ErrorCode.USERNAME_TAKEN),
          Map.entry("uk_users_email", ErrorCode.EMAIL_TAKEN),
          Map.entry("uk_student_branch_number", ErrorCode.STUDENT_NUMBER_TAKEN),
          Map.entry("uk_teacher_assignment", ErrorCode.TEACHER_ASSIGNMENT_EXISTS),
          Map.entry("uk_membership_active_student", ErrorCode.CONCURRENT_MODIFICATION),
          Map.entry("uk_student_guardian_student", ErrorCode.CONCURRENT_MODIFICATION));

  private ConstraintErrorCodes() {}

  public static Optional<ErrorCode> forConstraint(String constraintName) {
    if (constraintName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(CODES.get(constraintName.toLowerCase(Locale.ROOT)));
  }
}
