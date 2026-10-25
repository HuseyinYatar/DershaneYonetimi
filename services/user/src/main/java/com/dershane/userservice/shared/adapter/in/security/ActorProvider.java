package com.dershane.userservice.shared.adapter.in.security;

import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link Actor} from the validated JWT in the security context. Only inbound adapters
 * call it; use cases receive the actor as a parameter.
 */
@Slf4j
@Component
public class ActorProvider {

  private static final String ROLE_PREFIX = "ROLE_";

  public Actor require() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token)) {
      throw Rejections.reject(
          log,
          ErrorCode.AUTHENTICATION_REQUIRED,
          "No JWT authentication in security context",
          "authenticationType",
          authentication == null ? "none" : authentication.getClass().getSimpleName());
    }
    Jwt jwt = token.getToken();
    return new Actor(subject(jwt), roles(token), branchId(jwt));
  }

  private UUID subject(Jwt jwt) {
    try {
      return UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw Rejections.reject(
          log, ErrorCode.ACCESS_DENIED, "Token subject is not a UUID; caller cannot be resolved");
    }
  }

  private static Set<String> roles(JwtAuthenticationToken token) {
    return token.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith(ROLE_PREFIX))
        .map(authority -> authority.substring(ROLE_PREFIX.length()))
        .collect(Collectors.toUnmodifiableSet());
  }

  private UUID branchId(Jwt jwt) {
    String claim = jwt.getClaimAsString(Actor.BRANCH_CLAIM);
    if (claim == null || claim.isBlank()) {
      log.atDebug().addKeyValue("subject", jwt.getSubject()).log("Token has no branch_id claim");
      return null;
    }
    try {
      return UUID.fromString(claim);
    } catch (IllegalArgumentException ex) {
      log.atWarn()
          .addKeyValue("subject", jwt.getSubject())
          .log("Token branch_id claim is not a UUID; treating caller as having no branch");
      return null;
    }
  }
}
