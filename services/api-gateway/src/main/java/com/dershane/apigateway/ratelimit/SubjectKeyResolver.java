package com.dershane.apigateway.ratelimit;

import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Limits per user: the JWT principal name is the token's {@code sub}. Every {@code /api/**} request
 * is authenticated before the limiter runs, so the key is never empty.
 */
@Component
public class SubjectKeyResolver implements KeyResolver {

  @Override
  public Mono<String> resolve(ServerWebExchange exchange) {
    return exchange.getPrincipal().map(Principal::getName);
  }
}
