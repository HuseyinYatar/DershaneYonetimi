package com.dershane.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.dershane.userservice.support.EventQueue;
import com.dershane.userservice.support.EventQueue.Received;
import com.dershane.userservice.support.TestContainers;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.resource.UsersResource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end scenario on the running application with real PostgreSQL, Keycloak (project realm) and
 * Floci (SNS/SQS). Only token validation is replaced: the JwtDecoder resolves test token strings to
 * Keycloak-shaped JWTs, so the rest of the security chain runs unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceIT {

  private static final Path EVENTS_DIR = Path.of("..", "..", "shared", "events");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static EventQueue events;

  @DynamicPropertySource
  static void environment(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestContainers.postgres().getJdbcUrl());
    registry.add("spring.datasource.username", () -> TestContainers.postgres().getUsername());
    registry.add("spring.datasource.password", () -> TestContainers.postgres().getPassword());
    registry.add("user.keycloak.server-url", () -> TestContainers.keycloak().getAuthServerUrl());
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri",
        () ->
            TestContainers.keycloak().getAuthServerUrl()
                + "/realms/dershane/protocol/openid-connect/token");
    registry.add(
        "spring.security.oauth2.client.registration.keycloak-admin.client-secret",
        () -> "user-service-local-secret");
    registry.add("user.aws.region", () -> TestContainers.REGION);
    registry.add("user.aws.endpoint-url", () -> TestContainers.floci().getEndpoint());
    registry.add("user.aws.static-access-key-id", () -> TestContainers.floci().getAccessKey());
    registry.add("user.aws.static-secret-access-key", () -> TestContainers.floci().getSecretKey());
    registry.add("user.outbox.topic-arn", () -> eventQueue().topicArn());
    registry.add("user.outbox.publish-interval", () -> "200ms");
    registry.add("springdoc.api-docs.enabled", () -> "true");
    registry.add("management.server.port", () -> "0");
    registry.add("GIT_COMMIT_SHA", () -> "it-commit");
  }

  private static synchronized EventQueue eventQueue() {
    if (events == null) {
      events = EventQueue.create(TestContainers.floci());
    }
    return events;
  }

  @MockitoBean private JwtDecoder jwtDecoder;
  @LocalServerPort private int port;
  @LocalManagementPort private int managementPort;

  private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
  private final UUID superAdminId = UUID.randomUUID();
  private UUID branchId;
  private String branchCode;
  private UUID classroomA;
  private UUID classroomB;
  private UUID studentId;
  private UUID guardianId;
  private String studentUsername;

  @BeforeEach
  void resolveTestTokens() {
    when(jwtDecoder.decode(anyString()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(tokens.get(invocation.<String>getArgument(0)))
                    .orElseThrow(() -> new BadJwtException("Unknown test token")));
  }

  @Test
  @Order(1)
  void superAdminSetsUpBranchAndClassrooms() {
    JsonNode branch =
        call(
                "POST",
                "/api/v1/branches",
                superAdmin(),
                Map.of("name", "Kadıköy", "code", "kdk" + suffix()))
            .getBody();
    branchId = UUID.fromString(branch.get("id").asString());
    branchCode = branch.get("code").asString();
    classroomA = createClassroom("12-A");
    classroomB = createClassroom("12-B");
    assertThat(branchCode).isUpperCase();
  }

  @Test
  @Order(2)
  void studentWithoutEmailGetsTemporaryPasswordAndRetryDoesNotDuplicate() {
    Map<String, Object> body =
        Map.of(
            "branchId",
            branchId,
            "firstName",
            "Ali",
            "lastName",
            "Kaya",
            "studentNumber",
            "1042",
            "gradeLevel",
            12,
            "targetExam",
            "YKS");

    ResponseEntity<JsonNode> first = call("POST", "/api/v1/students", superAdmin(), body, "key-1");
    ResponseEntity<JsonNode> retry = call("POST", "/api/v1/students", superAdmin(), body, "key-1");

    assertThat(first.getStatusCode().value()).isEqualTo(201);
    studentId = UUID.fromString(first.getBody().at("/user/id").asString());
    studentUsername = first.getBody().at("/user/username").asString();
    assertThat(studentUsername).isEqualTo((branchCode + "-1042").toLowerCase());
    assertThat(first.getBody().get("temporaryPassword").asString()).hasSize(12);

    assertThat(retry.getStatusCode().value()).isEqualTo(201);
    assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    assertThat(retry.getBody().get("temporaryPassword").isNull()).isTrue();
    assertThat(retry.getBody().get("temporaryPasswordAlreadyIssued").asBoolean()).isTrue();
    assertThat(keycloakUsers().searchByUsername(studentUsername, true)).hasSize(1);
  }

  @Test
  @Order(3)
  void guardianIsLinkedAndStudentMovesBetweenClassrooms() {
    JsonNode guardian =
        call(
                "POST",
                "/api/v1/guardians",
                superAdmin(),
                Map.of(
                    "branchId",
                    branchId,
                    "firstName",
                    "Ayşe",
                    "lastName",
                    "Kaya",
                    "phone",
                    "+90 555 " + (100 + (int) (Math.random() * 800)) + " 22 33"))
            .getBody();
    guardianId = UUID.fromString(guardian.at("/user/id").asString());

    call(
        "PUT",
        "/api/v1/students/" + studentId + "/guardian",
        superAdmin(),
        Map.of("guardianId", guardianId, "relationship", "MOTHER"));
    JsonNode first =
        call(
                "PUT",
                "/api/v1/students/" + studentId + "/classroom",
                superAdmin(),
                Map.of("classroomId", classroomA))
            .getBody();
    JsonNode moved =
        call(
                "PUT",
                "/api/v1/students/" + studentId + "/classroom",
                superAdmin(),
                Map.of("classroomId", classroomB))
            .getBody();

    assertThat(moved.get("closedMembershipId").asString())
        .isEqualTo(first.get("membershipId").asString());
  }

  @Test
  @Order(4)
  void guardianSeesOwnChildButNotOthers() {
    String guardian = token(guardianId, "guardian");
    String stranger = token(UUID.randomUUID(), "guardian");

    assertThat(call("GET", "/api/v1/students/" + studentId, guardian, null).getStatusCode().value())
        .isEqualTo(200);
    ResponseEntity<JsonNode> denied = call("GET", "/api/v1/students/" + studentId, stranger, null);
    assertThat(denied.getStatusCode().value()).isEqualTo(403);
    assertThat(denied.getBody().get("errorCode").asString()).isEqualTo("ACCESS_DENIED");
  }

  @Test
  @Order(5)
  void deactivationDisablesLoginAndClosesTheClassroom() {
    JsonNode user =
        call("POST", "/api/v1/users/" + studentId + "/deactivate", superAdmin(), null).getBody();

    assertThat(user.get("active").asBoolean()).isFalse();
    assertThat(keycloakUsers().get(studentId.toString()).toRepresentation().isEnabled()).isFalse();
  }

  @Test
  @Order(6)
  void eventsReachConsumersMatchingTheirSchemas() throws IOException {
    // student, guardian, deactivation: 3x UserUpdated; link; 2x added; 2x removed (move,
    // deactivate)
    List<Received> received = eventQueue().receive(8, Duration.ofSeconds(30));

    assertThat(received)
        .extracting(Received::eventType)
        .containsOnly(
            "UserUpdated",
            "GuardianLinked",
            "StudentAddedToClassroom",
            "StudentRemovedFromClassroom");
    assertThat(received)
        .filteredOn(r -> r.eventType().equals("StudentAddedToClassroom"))
        .hasSize(2);
    assertThat(received)
        .filteredOn(r -> r.eventType().equals("StudentRemovedFromClassroom"))
        .hasSize(2);
    for (Received event : received) {
      assertThat(event.attributes()).containsEntry("eventType", event.eventType());
      assertMatchesSchema("envelope.schema.json", event.envelope());
      assertMatchesSchema("user/" + event.eventType() + ".schema.json", event.data());
    }
  }

  @Test
  @Order(7)
  void operationalEndpointsExposeHealthMetricsAndBuildInfo() throws IOException {
    RestClient management = client("http://localhost:" + managementPort);
    String metrics = management.get().uri("/metrics").retrieve().body(String.class);
    String readiness = management.get().uri("/health/readiness").retrieve().body(String.class);
    String info = management.get().uri("/info").retrieve().body(String.class);

    assertThat(readiness).contains("UP");
    assertThat(metrics)
        .contains("user_registrations_total{application=\"user\",role=\"STUDENT\"}")
        .contains("outbox_events_published_total")
        .contains("business_rule_violations_total")
        .contains("http_server_requests_seconds");
    assertThat(info).contains("it-commit");
    String sbom = management.get().uri("/sbom/application").retrieve().body(String.class);
    assertThat(sbom).contains("CycloneDX");
  }

  @Test
  @Order(8)
  void openApiDocumentIsExportedForTheFrontend() throws IOException {
    String document =
        client("http://localhost:" + port).get().uri("/v3/api-docs").retrieve().body(String.class);

    Path output = Path.of("target", "openapi", "openapi.json");
    Files.createDirectories(output.getParent());
    Files.writeString(output, document);
    assertThat(document).contains("/api/v1/students").contains("Idempotency-Key");
  }

  private UUID createClassroom(String name) {
    JsonNode classroom =
        call(
                "POST",
                "/api/v1/classrooms",
                superAdmin(),
                Map.of("branchId", branchId, "name", name, "gradeLevel", 12, "term", "2026-2027"))
            .getBody();
    return UUID.fromString(classroom.get("id").asString());
  }

  private ResponseEntity<JsonNode> call(String method, String path, String token, Object body) {
    return call(method, path, token, body, null);
  }

  private ResponseEntity<JsonNode> call(
      String method, String path, String token, Object body, String idempotencyKey) {
    RestClient.RequestBodySpec request =
        client("http://localhost:" + port)
            .method(org.springframework.http.HttpMethod.valueOf(method))
            .uri(path)
            .header("Authorization", "Bearer " + token);
    if (idempotencyKey != null) {
      request.header("Idempotency-Key", idempotencyKey);
    }
    if (body != null) {
      request.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
    }
    return request.retrieve().toEntity(JsonNode.class);
  }

  private static RestClient client(String baseUrl) {
    return RestClient.builder()
        .baseUrl(baseUrl)
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }

  private String superAdmin() {
    return tokens.containsKey("super-admin")
        ? "super-admin"
        : register("super-admin", superAdminId, UUID.randomUUID(), "admin", "super-admin");
  }

  private String token(UUID userId, String... roles) {
    return register(userId + String.join(",", roles), userId, branchId, roles);
  }

  private String register(String value, UUID userId, UUID branch, String... roles) {
    tokens.put(
        value,
        Jwt.withTokenValue(value)
            .header("alg", "none")
            .subject(userId.toString())
            .claim("branch_id", branch.toString())
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build());
    return value;
  }

  private static UsersResource keycloakUsers() {
    return TestContainers.keycloak().getKeycloakAdminClient().realm("dershane").users();
  }

  private static String suffix() {
    return UUID.randomUUID().toString().substring(0, 6);
  }

  private static void assertMatchesSchema(String schemaFile, JsonNode instance) throws IOException {
    try (InputStream input = Files.newInputStream(EVENTS_DIR.resolve(schemaFile))) {
      assertThat(
              SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                  .getSchema(input)
                  .validate(instance))
          .as("%s against %s", instance, schemaFile)
          .isEmpty();
    }
  }
}
