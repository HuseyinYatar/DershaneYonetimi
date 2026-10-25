package com.dershane.userservice.user.application.port.in;

import java.util.UUID;

/** Null fields are left unchanged. Carries personal data: {@code toString} is masked. */
public record UpdateTeacherCommand(
    String firstName, String lastName, String email, String phone, UUID specialtySubjectId) {

  @Override
  public String toString() {
    return "UpdateTeacherCommand[masked]";
  }
}
