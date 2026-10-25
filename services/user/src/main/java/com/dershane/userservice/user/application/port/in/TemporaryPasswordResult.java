package com.dershane.userservice.user.application.port.in;

/** The new temporary password; shown once to the admin, never stored or logged. */
public record TemporaryPasswordResult(String temporaryPassword) {

  @Override
  public String toString() {
    return "TemporaryPasswordResult[masked]";
  }
}
