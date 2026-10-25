package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.user.domain.Role;
import java.time.Instant;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record UserView(
    UUID id,
    UUID branchId,
    Role role,
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
    return "UserView[id=" + id + ", role=" + role + "]";
  }
}
