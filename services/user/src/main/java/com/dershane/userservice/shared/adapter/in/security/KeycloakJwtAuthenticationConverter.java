package com.dershane.userservice.shared.adapter.in.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Reads Keycloak realm roles from {@code realm_access.roles} and uses {@code sub} as the name. */
@Slf4j
@Component
public class KeycloakJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String REALM_ACCESS_CLAIM = "realm_access";
  private static final String ROLES_KEY = "roles";

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
  }

  private Collection<GrantedAuthority> authorities(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
    if (realmAccess == null || !(realmAccess.get(ROLES_KEY) instanceof List<?> roles)) {
      log.atWarn()
          .addKeyValue("subject", jwt.getSubject())
          .log("Token has no realm roles; caller gets no authorities");
      return List.of();
    }
    return roles.stream()
        .map(String::valueOf)
        .map(KeycloakJwtAuthenticationConverter::toAuthority)
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast)
        .toList();
  }

  static String toAuthority(String realmRole) {
    return "ROLE_" + realmRole.toUpperCase(Locale.ROOT).replace('-', '_');
  }
}
