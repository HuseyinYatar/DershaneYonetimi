package com.dershane.userservice.user.application.port.in;

/** Null fields are left unchanged. Carries personal data: {@code toString} is masked. */
public record UpdateAdminCommand(String firstName, String lastName, String email, String phone) {

  @Override
  public String toString() {
    return "UpdateAdminCommand[masked]";
  }
}
