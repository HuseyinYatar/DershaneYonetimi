package com.dershane.userservice.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.dershane.userservice.shared.application.Actor;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Keycloak-shaped tokens for tests: {@code sub}, {@code branch_id}, {@code realm_access}. */
public final class TestJwts {

  private TestJwts() {}

  public static JwtRequestPostProcessor token(UUID userId, UUID branchId, String... realmRoles) {
    return jwt()
        .jwt(
            builder ->
                builder
                    .subject(userId.toString())
                    .claim("branch_id", branchId.toString())
                    .claim("realm_access", Map.of("roles", List.of(realmRoles))))
        .authorities(
            Arrays.stream(realmRoles)
                .map(role -> "ROLE_" + role.toUpperCase(Locale.ROOT).replace('-', '_'))
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList());
  }

  /** The caller as a use case sees it; roles are upper-case, e.g. {@code "ADMIN"}. */
  public static Actor actor(UUID userId, UUID branchId, String... roles) {
    return new Actor(userId, Set.of(roles), branchId);
  }
}
