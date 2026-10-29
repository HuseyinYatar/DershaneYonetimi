package com.dershane.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.apigateway.support.StubServer;
import com.dershane.apigateway.support.TestJwts;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the whole gateway against a real Redis, a stub upstream and a stub JWKS endpoint. Needs
 * Docker (runs in {@code mvn verify}).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayIT {

  private static final String ORIGIN = "http://localhost:3000";

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  static final TestJwts JWTS = new TestJwts();
  static final StubServer JWKS = new StubServer("application/json", JWTS.jwks());
  static final StubServer USER_UPSTREAM = new StubServer("application/json", "{\"ok\":true}");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestJwts.ISSUER);
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWKS::url);
    registry.add("management.server.port", () -> "0");

    // Small bucket so the limit test needs few requests; every test uses its own subject.
    registry.add("RATE_LIMIT_REPLENISH_RATE", () -> "1");
    registry.add("RATE_LIMIT_BURST", () -> "5");

    // A list property is replaced as a whole, so both routes are declared here.
    String routes = "spring.cloud.gateway.server.webflux.routes";
    registry.add(routes + "[0].id", () -> "user");
    registry.add(routes + "[0].uri", USER_UPSTREAM::url);
    registry.add(routes + "[0].predicates[0]", () -> "Path=/api/v1/me/**");
    registry.add(routes + "[1].id", () -> "down");
    registry.add(routes + "[1].uri", () -> "http://localhost:" + unusedPort());
    registry.add(routes + "[1].predicates[0]", () -> "Path=/api/v1/down/**");
  }

  @AfterAll
  static void stopStubs() {
    JWKS.close();
    USER_UPSTREAM.close();
  }

  @Autowired WebTestClient client;

  @Test
  void requestWithoutTokenIsRejectedWithATurkishProblem() {
    client
        .get()
        .uri("/api/v1/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("AUTHENTICATION_REQUIRED")
        .jsonPath("$.detail")
        .isEqualTo("Lütfen giriş yapın.");
  }

  @Test
  void garbageTokenIsRejected() {
    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth("not-a-jwt"))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("AUTHENTICATION_REQUIRED");
  }

  @Test
  void tokenForAnotherAudienceIsRejected() {
    String token =
        JWTS.token(subject(), TestJwts.ISSUER, "some-other-api", Instant.now().plusSeconds(300));

    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void tokenWithoutAudienceIsRejected() {
    String token = JWTS.token(subject(), TestJwts.ISSUER, null, Instant.now().plusSeconds(300));

    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void tokenFromAnotherIssuerIsRejected() {
    String token =
        JWTS.token(
            subject(),
            "https://evil.example/realms/x",
            TestJwts.AUDIENCE,
            Instant.now().plusSeconds(300));

    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void expiredTokenIsRejected() {
    String token =
        JWTS.token(subject(), TestJwts.ISSUER, TestJwts.AUDIENCE, Instant.now().minusSeconds(3600));

    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void validTokenIsForwardedUntouchedAndAddsNoIdentityHeaders() {
    String token = JWTS.token(subject());

    client
        .get()
        .uri("/api/v1/me")
        .header(HttpHeaders.ORIGIN, ORIGIN)
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
        .expectBody()
        .jsonPath("$.ok")
        .isEqualTo(true);

    var headers = USER_UPSTREAM.lastRequestHeaders();
    assertThat(headers.get("authorization")).containsExactly("Bearer " + token);
    // Services decide from the token alone; the gateway must not hand them identity headers.
    assertThat(headers.keySet())
        .noneMatch(name -> name.startsWith("x-") && !name.startsWith("x-forwarded-"));
    // The trace context travels to the service, so one request is one trace across both.
    assertThat(headers).containsKey("traceparent");
  }

  @Test
  void preflightFromAnAllowedOriginNeedsNoToken() {
    client
        .options()
        .uri("/api/v1/me")
        .header(HttpHeaders.ORIGIN, ORIGIN)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN);
  }

  @Test
  void preflightFromAnotherOriginIsRefused() {
    client
        .options()
        .uri("/api/v1/me")
        .header(HttpHeaders.ORIGIN, "http://evil.example")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void callerIsRateLimitedPerSubject() {
    String token = JWTS.token(subject());
    int limited = 0;

    for (int i = 0; i < 20; i++) {
      var response = client.get().uri("/api/v1/me").headers(h -> h.setBearerAuth(token)).exchange();
      if (response.returnResult(String.class).getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
        limited++;
      }
    }
    assertThat(limited).isPositive();

    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("RATE_LIMITED");

    // Another caller still has a full bucket.
    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth(JWTS.token(subject())))
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void unreachableUpstreamGives503WhileOtherRoutesKeepWorking() {
    String token = JWTS.token(subject());

    client
        .get()
        .uri("/api/v1/down/anything")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("SERVICE_UNAVAILABLE")
        .jsonPath("$.detail")
        .isEqualTo("Servis şu anda yanıt vermiyor. Lütfen biraz sonra tekrar deneyin.");

    client
        .get()
        .uri("/api/v1/down/anything")
        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("The service is not responding right now. Please try again shortly.");

    client
        .get()
        .uri("/api/v1/me")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void pathWithoutARouteIs404() {
    client
        .get()
        .uri("/api/v1/nothing-here")
        .headers(h -> h.setBearerAuth(JWTS.token(subject())))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("ROUTE_NOT_FOUND");
  }

  @Test
  void pathsOutsideTheApiAreNeverServed() {
    client
        .get()
        .uri("/metrics")
        .headers(h -> h.setBearerAuth(JWTS.token(subject())))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("ACCESS_DENIED");
  }

  private static String subject() {
    return UUID.randomUUID().toString();
  }

  private static int unusedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port available", e);
    }
  }
}
