package com.dershane.userservice.user.adapter.in.web.dto;

import com.dershane.userservice.user.domain.Role;
import java.time.Instant;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record UserResponse(
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
    return "UserResponse[id=" + id + ", role=" + role + "]";
  }
}
