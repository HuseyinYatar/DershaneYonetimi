package com.dershane.userservice.user.application.port.in;

import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record RegisterAdminCommand(
    UUID branchId, String firstName, String lastName, String email, String phone) {

  @Override
  public String toString() {
    return "RegisterAdminCommand[branchId=" + branchId + "]";
  }
}
