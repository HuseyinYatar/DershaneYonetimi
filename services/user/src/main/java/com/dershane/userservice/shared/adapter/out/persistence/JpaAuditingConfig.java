package com.dershane.userservice.shared.adapter.out.persistence;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(
    auditorAwareRef = "auditorAware",
    dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

  /** Records the Keycloak {@code sub} of the caller; scheduled jobs run without a caller. */
  @Bean
  public AuditorAware<UUID> auditorAware() {
    return () -> {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (!(authentication instanceof JwtAuthenticationToken jwt)) {
        log.debug("No JWT caller in security context; audit fields left empty (system change)");
        return Optional.empty();
      }
      return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
    };
  }

  @Bean
  public DateTimeProvider auditingDateTimeProvider(Clock clock) {
    return () -> Optional.of(clock.instant());
  }
}
