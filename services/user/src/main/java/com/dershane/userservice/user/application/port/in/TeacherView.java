package com.dershane.userservice.user.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record TeacherView(
    UUID id,
    UUID branchId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String username,
    boolean active,
    UUID specialtySubjectId,
    Instant createdAt,
    Instant updatedAt) {

  @Override
  public String toString() {
    return "TeacherView[id=" + id + "]";
  }
}
