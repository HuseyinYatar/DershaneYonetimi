package com.dershane.apigateway.web;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

/**
 * Turns errors raised inside the gateway (rate limit, unreachable or slow upstream, no matching
 * route) into problem bodies. Errors it does not recognise, such as a malformed request, fall
 * through to Boot's default handler. Services' own error responses are proxied untouched.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorHandler implements WebExceptionHandler {

  private static final int MAX_CAUSE_DEPTH = 10;

  private final ProblemWriter problemWriter;

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(ex);
    }
    ErrorCode code = classify(ex);
    if (code == null) {
      return Mono.error(ex);
    }
    var event = code == ErrorCode.INTERNAL_ERROR ? log.atError().setCause(ex) : log.atWarn();
    event
        .addKeyValue("errorCode", code.name())
        .addKeyValue("reason", ex.getClass().getSimpleName())
        .addKeyValue("http.method", exchange.getRequest().getMethod().name())
        .addKeyValue("http.route", RequestLoggingFilter.routeIdOf(exchange))
        .log("Request failed in the gateway");
    return problemWriter.write(exchange, code);
  }

  /** Returns null for errors this handler does not own. */
  static ErrorCode classify(Throwable ex) {
    if (hasCause(ex, HttpClientErrorException.TooManyRequests.class)) {
      return ErrorCode.RATE_LIMITED;
    }
    // Timeouts first: Netty's ConnectTimeoutException is also a ConnectException.
    if (hasCause(
        ex, TimeoutException.class, ReadTimeoutException.class, ConnectTimeoutException.class)) {
      return ErrorCode.UPSTREAM_TIMEOUT;
    }
    if (hasCause(
        ex, ConnectException.class, UnknownHostException.class, PrematureCloseException.class)) {
      return ErrorCode.SERVICE_UNAVAILABLE;
    }
    if (ex instanceof ResponseStatusException status) {
      return switch (status.getStatusCode().value()) {
        case 404 -> ErrorCode.ROUTE_NOT_FOUND;
        case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
        case 504 -> ErrorCode.UPSTREAM_TIMEOUT;
        default -> null;
      };
    }
    return ErrorCode.INTERNAL_ERROR;
  }

  @SafeVarargs
  private static boolean hasCause(Throwable ex, Class<? extends Throwable>... types) {
    Throwable current = ex;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      for (Class<? extends Throwable> type : types) {
        if (type.isInstance(current)) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }
}
