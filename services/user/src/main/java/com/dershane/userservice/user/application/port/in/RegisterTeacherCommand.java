package com.dershane.userservice.user.application.port.in;

import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record RegisterTeacherCommand(
    UUID branchId,
    String firstName,
    String lastName,
    String email,
    String phone,
    UUID specialtySubjectId) {

  @Override
  public String toString() {
    return "RegisterTeacherCommand[branchId=" + branchId + "]";
  }
}
