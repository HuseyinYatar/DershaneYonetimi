package com.dershane.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.apigateway.support.StubServer;
import com.dershane.apigateway.support.TestJwts;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The rate limiter lets requests through when Redis fails, but only after the Redis timeouts. With
 * the Lettuce default (60 s) every request stalled for a minute during an outage, so this runs with
 * the timeouts from application.yml and checks that requests still pass quickly.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RedisOutageIT {

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static final TestJwts JWTS = new TestJwts();
  static final StubServer JWKS = new StubServer("application/json", JWTS.jwks());
  static final StubServer UPSTREAM = new StubServer("application/json", "{\"ok\":true}");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestJwts.ISSUER);
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS::url);
    registry.add("management.server.port", () -> "0");

    String routes = "spring.cloud.gateway.server.webflux.routes";
    registry.add(routes + "[0].id", () -> "user");
    registry.add(routes + "[0].uri", UPSTREAM::url);
    registry.add(routes + "[0].predicates[0]", () -> "Path=/api/v1/me/**");
  }

  @AfterAll
  static void stopStubs() {
    JWKS.close();
    UPSTREAM.close();
  }

  @Autowired WebTestClient client;

  @Test
  void requestsPassQuicklyWhileRedisIsDown() {
    String token = JWTS.token(UUID.randomUUID().toString());
    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk();

    REDIS.stop();

    long start = System.nanoTime();
    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
  }
}
