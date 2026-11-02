package com.dershane.apigateway.security;

import com.dershane.apigateway.web.ErrorCode;
import com.dershane.apigateway.web.ProblemWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 401: missing, expired or invalid token. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

  private final ProblemWriter problemWriter;

  @Override
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
    log.atWarn()
        .addKeyValue("errorCode", ErrorCode.AUTHENTICATION_REQUIRED.name())
        .addKeyValue("reason", ex.getClass().getSimpleName())
        .addKeyValue("http.method", exchange.getRequest().getMethod().name())
        .log("Request rejected: authentication failed");
    // A token was sent but is not acceptable, versus no token at all (RFC 6750).
    String challenge =
        ex instanceof OAuth2AuthenticationException ? "Bearer error=\"invalid_token\"" : "Bearer";
    exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, challenge);
    return problemWriter.write(exchange, ErrorCode.AUTHENTICATION_REQUIRED);
  }
}
