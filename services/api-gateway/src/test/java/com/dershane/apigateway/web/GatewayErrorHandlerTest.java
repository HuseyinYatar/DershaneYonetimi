package com.dershane.apigateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class GatewayErrorHandlerTest {

  private final GatewayErrorHandler handler = new GatewayErrorHandler(problemWriter());

  static ProblemWriter problemWriter() {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
    messages.setFallbackToSystemLocale(false);
    return new ProblemWriter(messages, JsonMapper.builder().build());
  }

  @Test
  void rateLimitExceptionMapsToRateLimited() {
    Throwable tooMany =
        HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS, "", HttpHeaders.EMPTY, new byte[0], null);

    assertThat(GatewayErrorHandler.classify(tooMany)).isEqualTo(ErrorCode.RATE_LIMITED);
  }

  @Test
  void timeoutsMapToUpstreamTimeout() {
    assertThat(
            GatewayErrorHandler.classify(
                new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT, "slow", ReadTimeoutException.INSTANCE)))
        .isEqualTo(ErrorCode.UPSTREAM_TIMEOUT);
    // Netty's ConnectTimeoutException is a ConnectException too; timeout must win.
    assertThat(GatewayErrorHandler.classify(new ConnectTimeoutException("connect timed out")))
        .isEqualTo(ErrorCode.UPSTREAM_TIMEOUT);
  }

  @Test
  void connectFailuresMapToServiceUnavailableEvenWhenWrapped() {
    Throwable wrapped =
        new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "boom", new ConnectException("refused"));

    assertThat(GatewayErrorHandler.classify(wrapped)).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
  }

  @Test
  void responseStatusExceptionsMapByStatus() {
    assertThat(GatewayErrorHandler.classify(new ResponseStatusException(HttpStatus.NOT_FOUND)))
        .isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
    assertThat(GatewayErrorHandler.classify(new ResponseStatusException(HttpStatus.BAD_REQUEST)))
        .isNull();
  }

  @Test
  void unknownExceptionsMapToInternalError() {
    assertThat(GatewayErrorHandler.classify(new IllegalStateException("bug")))
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void writesTurkishProblemByDefault() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/nothing"));

    handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    String body = exchange.getResponse().getBodyAsString().block();
    assertThat(body)
        .contains("\"errorCode\":\"ROUTE_NOT_FOUND\"")
        .contains("\"status\":404")
        .contains("İstenen adres bulunamadı.");
  }

  @Test
  void writesEnglishWhenAskedFor() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/me").header("Accept-Language", "en-US,en;q=0.9"));

    handler.handle(exchange, new ConnectException("refused")).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(exchange.getResponse().getBodyAsString().block())
        .contains("\"errorCode\":\"SERVICE_UNAVAILABLE\"")
        .contains("The service is not responding right now.");
  }

  @Test
  void unrecognisedErrorsFallThroughToTheNextHandler() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/me"));
    ResponseStatusException badRequest = new ResponseStatusException(HttpStatus.BAD_REQUEST);

    assertThatThrownBy(() -> handler.handle(exchange, badRequest).block()).isSameAs(badRequest);
  }

  @Test
  void committedResponsesAreLeftAlone() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/me"));
    exchange.getResponse().setComplete().block();
    IllegalStateException failure = new IllegalStateException("late");

    assertThatThrownBy(() -> handler.handle(exchange, failure).block()).isSameAs(failure);
  }
}
