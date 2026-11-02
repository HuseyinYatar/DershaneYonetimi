package com.dershane.apigateway.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Writes one log line per response: method, route id, status, duration.
 *
 * <p>The line is written just before the response is committed, so it also covers responses
 * produced by the security chain and the error handler. The duration is the time until the response
 * starts, not until the body ends. The query string, headers and body are never logged, so no
 * personal data reaches the logs. It runs right after the tracing filter, so the line carries
 * traceId and spanId.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements WebFilter, Ordered {

  private static final String UNMATCHED_ROUTE = "UNMATCHED";

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 2;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    long start = System.nanoTime();
    exchange
        .getResponse()
        .beforeCommit(
            () -> {
              log.atInfo()
                  .addKeyValue("http.method", exchange.getRequest().getMethod().name())
                  .addKeyValue("http.route", routeIdOf(exchange))
                  .addKeyValue("http.status", exchange.getResponse().getStatusCode().value())
                  .addKeyValue("duration_ms", (System.nanoTime() - start) / 1_000_000)
                  .log("HTTP request completed");
              return Mono.empty();
            });
    return chain.filter(exchange);
  }

  static String routeIdOf(ServerWebExchange exchange) {
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    return route == null ? UNMATCHED_ROUTE : route.getId();
  }
}
