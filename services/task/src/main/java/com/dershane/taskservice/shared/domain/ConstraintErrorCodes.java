package com.dershane.taskservice.shared.domain;

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
      Map.ofEntries(Map.entry("uk_assignment_task_student_date", ErrorCode.DATA_CONFLICT));

  private ConstraintErrorCodes() {}

  public static Optional<ErrorCode> forConstraint(String constraintName) {
    if (constraintName == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(CODES.get(constraintName.toLowerCase(Locale.ROOT)));
  }
}
