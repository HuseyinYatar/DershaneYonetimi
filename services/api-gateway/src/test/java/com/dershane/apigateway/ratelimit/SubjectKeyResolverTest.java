package com.dershane.apigateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class SubjectKeyResolverTest {

  private final SubjectKeyResolver resolver = new SubjectKeyResolver();

  @Test
  void keyIsThePrincipalName() {
    Principal principal = () -> "6f1c0a3e-0000-0000-0000-000000000001";
    ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/me"))
            .mutate()
            .principal(Mono.just(principal))
            .build();

    assertThat(resolver.resolve(exchange).block())
        .isEqualTo("6f1c0a3e-0000-0000-0000-000000000001");
  }
}
