package com.dershane.userservice.user.application.port.out;

/** Profile fields mirrored into the login account. Personal data: {@code toString} is masked. */
public record IdentityProfile(String email, String firstName, String lastName) {

  @Override
  public String toString() {
    return "IdentityProfile[masked]";
  }
}
