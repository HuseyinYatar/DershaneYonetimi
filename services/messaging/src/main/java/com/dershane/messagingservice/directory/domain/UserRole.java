package com.dershane.messagingservice.directory.domain;

import java.util.Optional;

public enum UserRole {
  STUDENT,
  TEACHER;

  /** The conversation roles; guardians and admins have none. */
  public static Optional<UserRole> parse(String name) {
    for (UserRole role : values()) {
      if (role.name().equals(name)) {
        return Optional.of(role);
      }
    }
    return Optional.empty();
  }
}
