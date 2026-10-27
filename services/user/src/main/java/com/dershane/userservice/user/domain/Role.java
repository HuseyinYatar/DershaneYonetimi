package com.dershane.userservice.user.domain;

import java.util.Locale;

/** Business role of a user (diagram: Rol). A super admin is an ADMIN with an extra realm role. */
public enum Role {
  STUDENT,
  TEACHER,
  GUARDIAN,
  ADMIN;

  /** Keycloak realm role name, e.g. {@code student}. */
  public String realmRole() {
    return name().toLowerCase(Locale.ROOT);
  }
}
