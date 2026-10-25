package com.dershane.userservice.user.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record CreateAdminRequest(
    @NotNull UUID branchId,
    @NotBlank @Size(max = 80) String firstName,
    @NotBlank @Size(max = 80) String lastName,
    @NotBlank @Email @Size(max = 254) String email,
    @Pattern(regexp = UserPatterns.PHONE) String phone) {

  @Override
  public String toString() {
    return "CreateAdminRequest[branchId=" + branchId + "]";
  }
}
