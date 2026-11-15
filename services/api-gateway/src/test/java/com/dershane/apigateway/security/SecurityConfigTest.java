package com.dershane.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class SecurityConfigTest {

  private static boolean matchesPreflight(MockServerHttpRequest.BaseBuilder<?> request) {
    return SecurityConfig.isCorsPreflight(MockServerWebExchange.from(request)).block().isMatch();
  }

  @Test
  void anOptionsRequestWithOriginAndRequestedMethodIsAPreflight() {
    assertThat(
            matchesPreflight(
                MockServerHttpRequest.options("/api/v1/me")
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")))
        .isTrue();
  }

  @Test
  void anOptionsRequestWithoutThoseHeadersIsAnOrdinaryRequestThatNeedsAToken() {
    assertThat(matchesPreflight(MockServerHttpRequest.options("/api/v1/users/42"))).isFalse();
    assertThat(
            matchesPreflight(
                MockServerHttpRequest.options("/api/v1/users/42")
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000")))
        .isFalse();
  }

  @Test
  void aGetRequestIsNeverAPreflight() {
    assertThat(
            matchesPreflight(
                MockServerHttpRequest.get("/api/v1/me")
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")))
        .isFalse();
  }
}
