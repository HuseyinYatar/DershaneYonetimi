package com.dershane.userservice.user.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record GuardianResponse(
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
    return "GuardianResponse[id=" + id + "]";
  }
}
