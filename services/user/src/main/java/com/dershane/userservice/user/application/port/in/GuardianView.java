package com.dershane.userservice.user.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record GuardianView(
    UUID id,
    UUID branchId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String username,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  @Override
  public String toString() {
    return "GuardianView[id=" + id + "]";
  }
}
