package com.dershane.attendanceservice.shared.adapter.in.security;

/**
 * Role expressions for {@code @PreAuthorize}. Keycloak realm roles are lower-case with hyphens
 * ({@code super-admin}); the converter maps them to authorities in upper-case with underscores
 * ({@code ROLE_SUPER_ADMIN}).
 */
public final class SecurityRoles {

  /** SpEL for {@code @PreAuthorize}: branch admins and super admins. */
  public static final String ANY_ADMIN = "hasAnyRole('ADMIN', 'SUPER_ADMIN')";

  public static final String ONLY_SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

  /** Teachers (own sessions only, checked in the use case) and admins. */
  public static final String ANY_STAFF = "hasAnyRole('TEACHER', 'ADMIN', 'SUPER_ADMIN')";

  public static final String GUARDIAN = "hasRole('GUARDIAN')";

  /** Everyone who may look at attendance records; the use case narrows it to their own scope. */
  public static final String ANY_RECORD_VIEWER =
      "hasAnyRole('STUDENT', 'GUARDIAN', 'TEACHER', 'ADMIN', 'SUPER_ADMIN')";

  private SecurityRoles() {}
}
