package com.dershane.apigateway.security;

import com.dershane.apigateway.web.ErrorCode;
import com.dershane.apigateway.web.ProblemWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 403 from the URL rules: an authenticated caller asked for a path the gateway does not serve. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingAccessDeniedHandler implements ServerAccessDeniedHandler {

  private final ProblemWriter problemWriter;

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
    log.atWarn()
        .addKeyValue("errorCode", ErrorCode.ACCESS_DENIED.name())
        .addKeyValue("http.method", exchange.getRequest().getMethod().name())
        .log("Request rejected by URL authorization rules");
    return problemWriter.write(exchange, ErrorCode.ACCESS_DENIED);
  }
}
