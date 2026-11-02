package com.dershane.apigateway.security;

import org.springframework.boot.security.autoconfigure.actuate.web.reactive.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * The gateway only authenticates (signature via JWKS, issuer, audience). It does not look at roles
 * and it adds no identity headers: services validate the same token themselves and make every
 * authorization decision. CORS and rate limiting live here, nowhere else.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain apiSecurityFilterChain(
      ServerHttpSecurity http,
      LoggingAuthenticationEntryPoint authenticationEntryPoint,
      LoggingAccessDeniedHandler accessDeniedHandler) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(
            authorize ->
                authorize
                    // Management port (8081) is not exposed through the ingress.
                    .matchers(EndpointRequest.toAnyEndpoint())
                    .permitAll()
                    // CORS preflight carries no token; the gateway's CORS handling answers it.
                    .pathMatchers(HttpMethod.OPTIONS)
                    .permitAll()
                    .pathMatchers("/api/**")
                    .authenticated()
                    .anyExchange()
                    .denyAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .build();
  }
}
