package com.dershane.userservice.user.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Null fields are left unchanged. Carries personal data: {@code toString} is masked. */
public record UpdateGuardianRequest(
    @Size(min = 1, max = 80) String firstName,
    @Size(min = 1, max = 80) String lastName,
    @Email @Size(max = 254) String email,
    @Pattern(regexp = UserPatterns.PHONE) String phone) {

  @Override
  public String toString() {
    return "UpdateGuardianRequest[masked]";
  }
}
