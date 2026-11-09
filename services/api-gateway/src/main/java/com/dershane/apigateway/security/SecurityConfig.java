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
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
                    // Any other OPTIONS request is an ordinary request and needs a token like the
                    // rest; a blanket OPTIONS rule would also skip the rate limiter and reach the
                    // services unauthenticated.
                    .matchers(SecurityConfig::isCorsPreflight)
                    .permitAll()
                    // Browsers cannot send an Authorization header on a WebSocket handshake. The
                    // messaging service authenticates it with a one-time ticket from the query
                    // string (obtained with a JWT from POST /api/v1/chat/tickets).
                    .pathMatchers(HttpMethod.GET, "/api/v1/chat/ws")
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

  /** A CORS preflight is an OPTIONS request with both Origin and Access-Control-Request-Method. */
  static Mono<ServerWebExchangeMatcher.MatchResult> isCorsPreflight(ServerWebExchange exchange) {
    return CorsUtils.isPreFlightRequest(exchange.getRequest())
        ? ServerWebExchangeMatcher.MatchResult.match()
        : ServerWebExchangeMatcher.MatchResult.notMatch();
  }
}
