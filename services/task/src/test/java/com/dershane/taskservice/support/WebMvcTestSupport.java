package com.dershane.taskservice.support;

import com.dershane.taskservice.shared.adapter.in.security.ActorProvider;
import com.dershane.taskservice.shared.adapter.in.security.KeycloakJwtAuthenticationConverter;
import com.dershane.taskservice.shared.adapter.in.security.LoggingAccessDeniedHandler;
import com.dershane.taskservice.shared.adapter.in.security.LoggingAuthenticationEntryPoint;
import com.dershane.taskservice.shared.adapter.in.security.SecurityConfig;
import com.dershane.taskservice.shared.adapter.in.security.SecurityProblemWriter;
import com.dershane.taskservice.shared.adapter.in.web.CurrentActorArgumentResolver;
import com.dershane.taskservice.shared.adapter.in.web.ProblemDetailFactory;
import com.dershane.taskservice.shared.adapter.in.web.WebConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The production security chain, error handling and locale setup for {@code @WebMvcTest} slices.
 * Requests authenticate with {@link TestJwts}; real bearer tokens are rejected.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({
  SecurityConfig.class,
  KeycloakJwtAuthenticationConverter.class,
  LoggingAuthenticationEntryPoint.class,
  LoggingAccessDeniedHandler.class,
  SecurityProblemWriter.class,
  ProblemDetailFactory.class,
  ActorProvider.class,
  CurrentActorArgumentResolver.class,
  WebConfig.class
})
public class WebMvcTestSupport {

  @Bean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  JwtDecoder jwtDecoder() {
    return token -> {
      throw new BadJwtException("Test decoder accepts no real tokens");
    };
  }
}
