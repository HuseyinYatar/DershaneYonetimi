package com.dershane.taskservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.dershane.taskservice.assignment.application.port.in.GenerateTodayAssignmentsUseCase;
import com.dershane.taskservice.assignment.application.port.in.ProcessDeadlinesUseCase;
import com.dershane.taskservice.support.EventQueue;
import com.dershane.taskservice.support.EventQueue.Received;
import com.dershane.taskservice.support.TestContainers;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * End-to-end scenario on the running application with real PostgreSQL and Floci (SNS/SQS): a
 * teacher gives a daily task to a classroom, students submit, the teacher evaluates, the CronJobs
 * run, and the events reach a consumer queue. Only token validation is replaced: the JwtDecoder
 * resolves test token strings to Keycloak-shaped JWTs, so the rest of the security chain runs
 * unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskServiceIT {

  private static final Path EVENTS_DIR = Path.of("..", "..", "shared", "events");
  private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
  private static EventQueue events;

  @DynamicPropertySource
  static void environment(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestContainers.postgres().getJdbcUrl());
    registry.add("spring.datasource.username", () -> TestContainers.postgres().getUsername());
    registry.add("spring.datasource.password", () -> TestContainers.postgres().getPassword());
    registry.add("task.aws.region", () -> TestContainers.REGION);
    registry.add("task.aws.endpoint-url", () -> TestContainers.floci().getEndpoint());
    registry.add("task.aws.static-access-key-id", () -> TestContainers.floci().getAccessKey());
    registry.add("task.aws.static-secret-access-key", () -> TestContainers.floci().getSecretKey());
    registry.add("task.outbox.topic-arn", () -> eventQueue().topicArn());
    registry.add("task.outbox.publish-interval", () -> "200ms");
    // The replicas are fed through the admin endpoint here; UserEventsInboxIT covers the queue.
    registry.add("task.inbox.enabled", () -> "false");
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
  @Autowired private GenerateTodayAssignmentsUseCase generateToday;
  @Autowired private ProcessDeadlinesUseCase processDeadlines;
  @Autowired private JdbcTemplate jdbc;
  @LocalServerPort private int port;
  @LocalManagementPort private int managementPort;

  private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
  private final LocalDate today = LocalDate.now(ISTANBUL);
  private final UUID branchId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final UUID otherTeacherId = UUID.randomUUID();
  private final UUID ali = UUID.randomUUID();
  private final UUID ayse = UUID.randomUUID();
  private final UUID alisGuardian = UUID.randomUUID();
  private UUID taskId;
  private UUID aliAssignmentId;

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
  void adminFeedsTheLocalCopiesOfMembershipsAndGuardianLinks() {
    int number = 0;
    for (UUID student : List.of(ali, ayse)) {
      number++;
      ResponseEntity<JsonNode> response =
          call(
              "PUT",
              "/api/v1/task-replicas/memberships/" + UUID.randomUUID(),
              admin(),
              Map.of(
                  "studentId",
                  student,
                  "classroomId",
                  classroomId,
                  "branchId",
                  branchId,
                  "studentNumber",
                  "ST" + number,
                  "fullName",
                  "Student " + number,
                  "startDate",
                  today.minusDays(30).toString()),
              null);
      assertThat(response.getStatusCode().value()).isEqualTo(204);
    }
    assertThat(
            call(
                    "PUT",
                    "/api/v1/task-replicas/guardian-links/" + ali,
                    admin(),
                    Map.of("guardianId", alisGuardian),
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(204);
    assertThat(
            call(
                    "PUT",
                    "/api/v1/task-replicas/guardian-links/" + ali,
                    teacher(),
                    Map.of("guardianId", alisGuardian),
                    null)
                .getStatusCode()
                .value())
        .as("a teacher may not feed the copies")
        .isEqualTo(403);
  }

  @Test
  @Order(2)
  void teacherGivesADailyTaskToTheClassroomAndEveryStudentGetsAnAssignment() {
    Map<String, Object> body = dailyTaskBody(classroomId);
    ResponseEntity<JsonNode> created = call("POST", "/api/v1/tasks", teacher(), body, "task-1");
    taskId = UUID.fromString(created.getBody().get("id").asString());

    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertThat(
            call("POST", "/api/v1/tasks", teacher(), body, "task-1").getBody().get("id").asString())
        .as("the same Idempotency-Key replays the first answer")
        .isEqualTo(taskId.toString());
    assertThat(call("POST", "/api/v1/tasks", student(ali), body, null).getStatusCode().value())
        .isEqualTo(403);
    assertThat(
            call("POST", "/api/v1/tasks", teacher(), dailyTaskBody(UUID.randomUUID()), null)
                .getBody()
                .get("errorCode")
                .asString())
        .isEqualTo("TARGET_NOT_IN_BRANCH");

    ResponseEntity<JsonNode> mine =
        call("GET", "/api/v1/task-assignments", student(ali), null, null);
    assertThat(mine.getStatusCode().value()).isEqualTo(200);
    assertThat(mine.getBody().get("content")).hasSize(1);
    aliAssignmentId = UUID.fromString(mine.getBody().get("content").get(0).get("id").asString());
    assertThat(
            call("GET", "/api/v1/tasks/" + taskId + "/assignments", teacher(), null, null)
                .getBody()
                .get("content"))
        .hasSize(2);
    assertThat(
            call("GET", "/api/v1/tasks/" + taskId + "/assignments", otherTeacher(), null, null)
                .getStatusCode()
                .value())
        .isEqualTo(403);
  }

  @Test
  @Order(3)
  void generatingAgainCreatesNothingNew() {
    assertThat(generateToday.generateToday().created()).isZero();
  }

  @Test
  @Order(4)
  void guardianSeesTheChildsAssignmentButNotOthers() {
    assertThat(
            call(
                    "GET",
                    "/api/v1/task-assignments?studentId=" + ali,
                    guardian(alisGuardian),
                    null,
                    null)
                .getBody()
                .get("content"))
        .hasSize(1);
    assertThat(
            call(
                    "GET",
                    "/api/v1/task-assignments?studentId=" + ayse,
                    guardian(alisGuardian),
                    null,
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(403);
    assertThat(
            call("GET", "/api/v1/task-assignments/" + aliAssignmentId, student(ayse), null, null)
                .getStatusCode()
                .value())
        .isEqualTo(403);
  }

  @Test
  @Order(5)
  void studentSubmitsAndTeacherEvaluates() {
    String path = "/api/v1/task-assignments/" + aliAssignmentId;
    assertThat(
            call("PUT", path + "/evaluation", teacher(), Map.of("score", 80), null)
                .getBody()
                .get("errorCode")
                .asString())
        .isEqualTo("NOT_SUBMITTED");
    assertThat(
            call("PUT", path + "/submission", student(ayse), Map.of("description", "mine"), null)
                .getStatusCode()
                .value())
        .isEqualTo(403);

    ResponseEntity<JsonNode> submitted =
        call("PUT", path + "/submission", student(ali), Map.of("description", "Done"), null);
    assertThat(submitted.getStatusCode().value()).isEqualTo(200);
    assertThat(submitted.getBody().get("status").asString()).isEqualTo("SUBMITTED");

    assertThat(
            call("PUT", path + "/evaluation", otherTeacher(), Map.of("score", 80), null)
                .getStatusCode()
                .value())
        .isEqualTo(403);
    ResponseEntity<JsonNode> graded =
        call(
            "PUT",
            path + "/evaluation",
            teacher(),
            Map.of("score", 80, "feedback", "Well done"),
            null);
    assertThat(graded.getStatusCode().value()).isEqualTo(200);
    assertThat(graded.getBody().get("status").asString()).isEqualTo("GRADED");
    assertThat(
            call("PUT", path + "/submission", student(ali), Map.of("description", "again"), null)
                .getBody()
                .get("errorCode")
                .asString())
        .isEqualTo("ASSIGNMENT_ALREADY_GRADED");
  }

  @Test
  @Order(6)
  void eventsReachTheConsumerQueueAndMatchTheirSchemas() throws IOException {
    List<Received> received = eventQueue().receive(3, Duration.ofSeconds(20));

    assertThat(received)
        .extracting(Received::eventType)
        .contains("TaskAssigned", "SubmissionGraded");
    for (Received event : received) {
      assertMatchesSchema("task/" + event.eventType() + ".schema.json", event.data());
    }
  }

  @Test
  @Order(7)
  void deadlineJobMarksPastDueAssignmentsOverdue() {
    jdbc.update(
        "update assignment set due_at = ? where student_id = ? and status = 'ASSIGNED'",
        java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
        ayse);

    ProcessDeadlinesUseCase.DeadlineResult result = processDeadlines.processDeadlines();

    assertThat(result.overdue()).isEqualTo(1);
    assertThat(
            call("GET", "/api/v1/task-assignments?status=OVERDUE", student(ayse), null, null)
                .getBody()
                .get("content"))
        .hasSize(1);
  }

  @Test
  @Order(8)
  void deactivatedTaskStopsNewAssignmentsAndManagementEndpointsStayPrivate() {
    ResponseEntity<JsonNode> stopped =
        call("POST", "/api/v1/tasks/" + taskId + "/deactivate", teacher(), null, null);
    assertThat(stopped.getBody().get("active").asBoolean()).isFalse();
    assertThat(
            call("POST", "/api/v1/tasks/" + taskId + "/deactivate", teacher(), null, null)
                .getBody()
                .get("errorCode")
                .asString())
        .isEqualTo("TASK_INACTIVE");
    assertThat(
            client("http://localhost:" + managementPort)
                .get()
                .uri("/health/readiness")
                .retrieve()
                .toEntity(JsonNode.class)
                .getStatusCode()
                .value())
        .isEqualTo(200);
  }

  private Map<String, Object> dailyTaskBody(UUID classroom) {
    return Map.of(
        "title", "Chapter 4 problems",
        "description", "Solve all of them",
        "targetType", "CLASSROOM",
        "targetId", classroom,
        "recurrence", "DAILY",
        "startDate", today.toString(),
        "dueTime", "23:59:00");
  }

  private ResponseEntity<JsonNode> call(
      String method, String path, String token, Object body, String idempotencyKey) {
    RestClient.RequestBodySpec request =
        client("http://localhost:" + port)
            .method(HttpMethod.valueOf(method))
            .uri(path)
            .header("Authorization", "Bearer " + token);
    if (idempotencyKey != null) {
      request.header("Idempotency-Key", idempotencyKey);
    }
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    return request.retrieve().toEntity(JsonNode.class);
  }

  private static RestClient client(String baseUrl) {
    return RestClient.builder()
        .baseUrl(baseUrl)
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }

  private String admin() {
    return token(adminId, "admin");
  }

  private String teacher() {
    return token(teacherId, "teacher");
  }

  private String otherTeacher() {
    return token(otherTeacherId, "teacher");
  }

  private String guardian(UUID guardianId) {
    return token(guardianId, "guardian");
  }

  private String student(UUID studentId) {
    return token(studentId, "student");
  }

  private String token(UUID userId, String role) {
    String value = userId + "." + role;
    tokens.put(
        value,
        Jwt.withTokenValue(value)
            .header("alg", "none")
            .subject(userId.toString())
            .claim("branch_id", branchId.toString())
            .claim("realm_access", Map.of("roles", List.of(role)))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build());
    return value;
  }

  private static void assertMatchesSchema(String schemaFile, JsonNode instance) throws IOException {
    try (InputStream input = Files.newInputStream(EVENTS_DIR.resolve(schemaFile))) {
      var schema =
          SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(input);
      assertThat(schema.validate(instance)).as(schemaFile).isEmpty();
    }
  }
}
