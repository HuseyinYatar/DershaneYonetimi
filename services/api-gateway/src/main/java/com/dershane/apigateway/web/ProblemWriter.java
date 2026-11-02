package com.dershane.apigateway.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an RFC 9457 problem body with an {@code errorCode} and a localized {@code detail}. The
 * fields match the ones the services return, so the frontend handles both the same way.
 */
@Component
@RequiredArgsConstructor
public class ProblemWriter {

  private static final Locale TURKISH = Locale.forLanguageTag("tr");
  private static final List<Locale> SUPPORTED = List.of(TURKISH, Locale.ENGLISH);

  private final MessageSource messageSource;
  private final JsonMapper jsonMapper;

  public Mono<Void> write(ServerWebExchange exchange, ErrorCode code) {
    HttpStatus status = code.status();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", status.getReasonPhrase());
    body.put("status", status.value());
    body.put(
        "detail",
        messageSource.getMessage(
            code.messageKey(), null, code.name(), localeOf(exchange.getRequest())));
    body.put("errorCode", code.name());

    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    byte[] bytes = jsonMapper.writeValueAsBytes(body);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
  }

  /** Turkish unless the client explicitly asks for English (same rule as the services). */
  static Locale localeOf(ServerHttpRequest request) {
    try {
      Locale match = Locale.lookup(request.getHeaders().getAcceptLanguage(), SUPPORTED);
      return match != null ? match : TURKISH;
    } catch (IllegalArgumentException malformedHeader) {
      return TURKISH;
    }
  }
}
