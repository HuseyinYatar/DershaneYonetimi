package com.dershane.userservice.shared.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthenticationConverterTest {

  private final KeycloakJwtAuthenticationConverter converter =
      new KeycloakJwtAuthenticationConverter();

  @Test
  void mapsRealmRolesToAuthoritiesAndUsesSubjectAsName() {
    Jwt jwt =
        jwt(Map.of("realm_access", Map.of("roles", List.of("admin", "super-admin", "student"))));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token.getName()).isEqualTo("user-1");
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_STUDENT");
  }

  @Test
  void tokenWithoutRealmRolesGetsNoAuthorities() {
    AbstractAuthenticationToken token = converter.convert(jwt(Map.of("scope", "openid")));

    assertThat(token.getAuthorities()).isEmpty();
  }

  private static Jwt jwt(Map<String, Object> claims) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
    claims.forEach(builder::claim);
    return builder.build();
  }
}
