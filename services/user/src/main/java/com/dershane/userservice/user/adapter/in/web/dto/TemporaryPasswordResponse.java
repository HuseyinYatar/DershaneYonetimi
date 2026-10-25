package com.dershane.userservice.user.adapter.in.web.dto;

/** The temporary password is shown once and never logged: {@code toString} is masked. */
public record TemporaryPasswordResponse(String temporaryPassword) {

  @Override
  public String toString() {
    return "TemporaryPasswordResponse[masked]";
  }
}
