package com.dershane.userservice.shared.application;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller, taken from the validated JWT. It is the first parameter of every use
 * case that decides on the caller's identity; use cases never read the security context.
 *
 * @param id Keycloak {@code sub}, which is also the user's id in this service
 * @param roles role names without the {@code ROLE_} prefix, e.g. {@code ADMIN}
 * @param branchId {@code branch_id} claim; may be null for tokens without the claim
 */
public record Actor(UUID id, Set<String> roles, UUID branchId) {

  /** Keycloak user attribute (token claim) holding the user's branch. */
  public static final String BRANCH_CLAIM = "branch_id";

  public static final String STUDENT = "STUDENT";
  public static final String TEACHER = "TEACHER";
  public static final String GUARDIAN = "GUARDIAN";
  public static final String ADMIN = "ADMIN";
  public static final String SUPER_ADMIN = "SUPER_ADMIN";

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public boolean isSuperAdmin() {
    return hasRole(SUPER_ADMIN);
  }

  public boolean isAdmin() {
    return hasRole(ADMIN);
  }

  public boolean isTeacher() {
    return hasRole(TEACHER);
  }

  public boolean isGuardian() {
    return hasRole(GUARDIAN);
  }

  public boolean isStudent() {
    return hasRole(STUDENT);
  }
}
