package com.dershane.attendanceservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.schedule.application.port.in.GenerateSessionsResult;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateSessionsUseCase;
import com.dershane.attendanceservice.session.application.port.in.ExpireSessionsUseCase;
import com.dershane.attendanceservice.support.EventQueue;
import com.dershane.attendanceservice.support.EventQueue.Received;
import com.dershane.attendanceservice.support.TestContainers;
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
 * teacher takes attendance for a session, excuses and corrections change the outcome, and the
 * events reach a consumer queue. Only token validation is replaced: the JwtDecoder resolves test
 * token strings to Keycloak-shaped JWTs, so the rest of the security chain runs unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttendanceServiceIT {

  private static final Path EVENTS_DIR = Path.of("..", "..", "shared", "events");
  private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
  private static EventQueue events;

  @DynamicPropertySource
  static void environment(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestContainers.postgres().getJdbcUrl());
    registry.add("spring.datasource.username", () -> TestContainers.postgres().getUsername());
    registry.add("spring.datasource.password", () -> TestContainers.postgres().getPassword());
    registry.add("attendance.aws.region", () -> TestContainers.REGION);
    registry.add("attendance.aws.endpoint-url", () -> TestContainers.floci().getEndpoint());
    registry.add(
        "attendance.aws.static-access-key-id", () -> TestContainers.floci().getAccessKey());
    registry.add(
        "attendance.aws.static-secret-access-key", () -> TestContainers.floci().getSecretKey());
    registry.add("attendance.outbox.topic-arn", () -> eventQueue().topicArn());
    registry.add("attendance.outbox.publish-interval", () -> "200ms");
    // Lets a session of any hour today be taken, whatever time the test runs.
    registry.add("attendance.session.take-early-minutes", () -> "1440");
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
  @Autowired private GenerateSessionsUseCase generateSessions;
  @Autowired private ExpireSessionsUseCase expireSessions;
  @Autowired private JdbcTemplate jdbc;
  @LocalServerPort private int port;
  @LocalManagementPort private int managementPort;

  private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
  private final LocalDate today = LocalDate.now(ISTANBUL);
  private final UUID branchId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();
  private final UUID subjectId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final UUID otherTeacherId = UUID.randomUUID();
  private final UUID ali = UUID.randomUUID();
  private final UUID ayse = UUID.randomUUID();
  private final UUID can = UUID.randomUUID();
  private final UUID aysesGuardian = UUID.randomUUID();
  private final UUID alisGuardian = UUID.randomUUID();
  private UUID sessionId;

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
    for (UUID student : List.of(ali, ayse, can)) {
      number++;
      ResponseEntity<JsonNode> response =
          call(
              "PUT",
              "/api/v1/attendance-replicas/memberships/" + UUID.randomUUID(),
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
                    "/api/v1/attendance-replicas/guardian-links/" + ayse,
                    admin(),
                    Map.of("guardianId", aysesGuardian),
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(204);
    assertThat(
            call(
                    "PUT",
                    "/api/v1/attendance-replicas/guardian-links/" + ali,
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
  void adminOpensASessionAndClashesAreRefused() {
    Map<String, Object> body = sessionBody(classroomId, teacherId, 10);

    ResponseEntity<JsonNode> created =
        call("POST", "/api/v1/class-sessions", admin(), body, "open-1");
    sessionId = UUID.fromString(created.getBody().get("id").asString());

    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertThat(created.getBody().get("status").asString()).isEqualTo("PLANNED");
    ResponseEntity<JsonNode> sameClassroom =
        call(
            "POST",
            "/api/v1/class-sessions",
            admin(),
            sessionBody(classroomId, otherTeacherId, 10),
            null);
    assertThat(sameClassroom.getStatusCode().value()).isEqualTo(409);
    assertThat(sameClassroom.getBody().get("errorCode").asString())
        .isEqualTo("SESSION_CLASSROOM_OVERLAP");
    ResponseEntity<JsonNode> sameTeacher =
        call(
            "POST",
            "/api/v1/class-sessions",
            admin(),
            sessionBody(UUID.randomUUID(), teacherId, 10),
            null);
    assertThat(sameTeacher.getStatusCode().value()).isEqualTo(409);
    assertThat(sameTeacher.getBody().get("errorCode").asString()).isEqualTo("SESSION_TEACHER_BUSY");
    ResponseEntity<JsonNode> replay =
        call("POST", "/api/v1/class-sessions", admin(), body, "open-1");
    assertThat(replay.getBody().get("id").asString()).isEqualTo(sessionId.toString());
  }

  @Test
  @Order(3)
  void onlyTheSessionsTeacherSeesTheRoster() {
    ResponseEntity<JsonNode> roster = call("GET", rosterPath(), teacher(), null, null);

    assertThat(roster.getStatusCode().value()).isEqualTo(200);
    assertThat(roster.getBody().get("students")).hasSize(3);
    assertThat(roster.getBody().get("students").get(0).get("status").isNull()).isTrue();
    ResponseEntity<JsonNode> other = call("GET", rosterPath(), otherTeacher(), null, null);
    assertThat(other.getStatusCode().value()).isEqualTo(403);
    assertThat(other.getBody().get("errorCode").asString()).isEqualTo("NOT_SESSION_TEACHER");
  }

  @Test
  @Order(4)
  void anApprovedExcuseShowsOnTheRosterBeforeAttendanceIsTaken() {
    Map<String, Object> excuse =
        Map.of(
            "studentId",
            ayse,
            "fromDate",
            today.toString(),
            "toDate",
            today.toString(),
            "reason",
            "Doctor appointment");

    ResponseEntity<JsonNode> reported =
        call("POST", "/api/v1/excuses", guardian(aysesGuardian), excuse, null);
    UUID excuseId = UUID.fromString(reported.getBody().get("id").asString());

    assertThat(reported.getStatusCode().value()).isEqualTo(201);
    assertThat(reported.getBody().get("status").asString()).isEqualTo("PENDING");
    ResponseEntity<JsonNode> stranger =
        call(
            "POST",
            "/api/v1/excuses",
            guardian(alisGuardian),
            Map.of(
                "studentId",
                ayse,
                "fromDate",
                today.toString(),
                "toDate",
                today.toString(),
                "reason",
                "Not my child"),
            null);
    assertThat(stranger.getStatusCode().value()).isEqualTo(403);
    assertThat(stranger.getBody().get("errorCode").asString()).isEqualTo("GUARDIAN_NOT_LINKED");

    ResponseEntity<JsonNode> approved =
        call(
            "POST",
            "/api/v1/excuses/" + excuseId + "/approve",
            admin(),
            Map.of("note", "ok"),
            null);
    assertThat(approved.getBody().get("status").asString()).isEqualTo("APPROVED");
    ResponseEntity<JsonNode> again =
        call("POST", "/api/v1/excuses/" + excuseId + "/approve", admin(), null, null);
    assertThat(again.getStatusCode().value()).isEqualTo(409);
    assertThat(again.getBody().get("errorCode").asString()).isEqualTo("EXCUSE_ALREADY_DECIDED");

    JsonNode students = call("GET", rosterPath(), teacher(), null, null).getBody().get("students");
    assertThat(rowOf(students, ayse).get("excused").asBoolean()).isTrue();
    assertThat(rowOf(students, ali).get("excused").asBoolean()).isFalse();
  }

  @Test
  @Order(5)
  void aForgottenStudentRejectsTheSubmission() {
    ResponseEntity<JsonNode> response =
        call(
            "POST",
            attendancePath(),
            teacher(),
            Map.of("entries", List.of(entry(ali, "PRESENT"), entry(ayse, "PRESENT"))),
            null);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("errorCode").asString()).isEqualTo("ROSTER_MISMATCH");
  }

  @Test
  @Order(6)
  void theTeacherTakesAttendanceOnceAndRetriesDoNotDuplicate() {
    Map<String, Object> body =
        Map.of(
            "entries", List.of(entry(ali, "ABSENT"), entry(ayse, "ABSENT"), entry(can, "EXCUSED")));

    ResponseEntity<JsonNode> taken = call("POST", attendancePath(), teacher(), body, "take-1");
    ResponseEntity<JsonNode> retry = call("POST", attendancePath(), teacher(), body, "take-1");
    ResponseEntity<JsonNode> second = call("POST", attendancePath(), teacher(), body, "take-2");

    assertThat(taken.getStatusCode().value()).isEqualTo(201);
    // Ayse's approved excuse turns her ABSENT into EXCUSED, so one absent and two excused.
    assertThat(taken.getBody().get("absent").asInt()).isEqualTo(1);
    assertThat(taken.getBody().get("excused").asInt()).isEqualTo(2);
    assertThat(retry.getStatusCode().value()).isEqualTo(201);
    assertThat(retry.getBody().get("takenAt")).isEqualTo(taken.getBody().get("takenAt"));
    assertThat(second.getStatusCode().value()).isEqualTo(409);
    assertThat(second.getBody().get("errorCode").asString()).isEqualTo("ATTENDANCE_ALREADY_TAKEN");
    assertThat(
            call("GET", "/api/v1/class-sessions/" + sessionId, admin(), null, null)
                .getBody()
                .get("status")
                .asString())
        .isEqualTo("COMPLETED");
  }

  @Test
  @Order(7)
  void recordsAreVisibleOnlyWithinEachRolesScope() {
    JsonNode own = call("GET", "/api/v1/attendance-records", student(ali), null, null).getBody();
    assertThat(own.get("content")).hasSize(1);
    assertThat(own.get("content").get(0).get("status").asString()).isEqualTo("ABSENT");

    ResponseEntity<JsonNode> peer =
        call("GET", "/api/v1/attendance-records?studentId=" + ayse, student(ali), null, null);
    assertThat(peer.getStatusCode().value()).isEqualTo(403);

    JsonNode child =
        call(
                "GET",
                "/api/v1/attendance-records?studentId=" + ayse,
                guardian(aysesGuardian),
                null,
                null)
            .getBody();
    assertThat(child.get("content")).hasSize(1);
    assertThat(child.get("content").get(0).get("status").asString()).isEqualTo("EXCUSED");
    assertThat(child.get("content").get(0).get("excuseId").isNull()).isFalse();

    ResponseEntity<JsonNode> stranger =
        call(
            "GET",
            "/api/v1/attendance-records?studentId=" + ali,
            guardian(aysesGuardian),
            null,
            null);
    assertThat(stranger.getStatusCode().value()).isEqualTo(403);
    assertThat(stranger.getBody().get("errorCode").asString()).isEqualTo("GUARDIAN_NOT_LINKED");

    JsonNode ofTeacher = call("GET", "/api/v1/attendance-records", teacher(), null, null).getBody();
    assertThat(ofTeacher.get("content")).hasSize(3);
    JsonNode ofOtherTeacher =
        call("GET", "/api/v1/attendance-records", otherTeacher(), null, null).getBody();
    assertThat(ofOtherTeacher.get("content")).isEmpty();

    JsonNode summary =
        call("GET", "/api/v1/attendance-summaries?studentId=" + ayse, admin(), null, null)
            .getBody();
    assertThat(summary.get("excused").asInt()).isEqualTo(1);
    assertThat(summary.get("total").asInt()).isEqualTo(1);
  }

  @Test
  @Order(8)
  void correctionsNeedAReasonAndAreAudited() {
    String path = "/api/v1/class-sessions/" + sessionId + "/records/" + ali;

    ResponseEntity<JsonNode> noReason =
        call("PUT", path, teacher(), Map.of("status", "PRESENT", "reason", " "), null);
    ResponseEntity<JsonNode> toPresent =
        call(
            "PUT",
            path,
            teacher(),
            Map.of("status", "PRESENT", "reason", "He was in the lab"),
            null);
    ResponseEntity<JsonNode> backToAbsent =
        call(
            "PUT", path, teacher(), Map.of("status", "ABSENT", "reason", "Mis-click, sorry"), null);

    assertThat(noReason.getStatusCode().value()).isEqualTo(400);
    assertThat(toPresent.getBody().get("status").asString()).isEqualTo("PRESENT");
    assertThat(backToAbsent.getBody().get("status").asString()).isEqualTo("ABSENT");
    assertThat(jdbc.queryForObject("select count(*) from attendance_record_change", Long.class))
        .isEqualTo(2L);
  }

  @Test
  @Order(9)
  void aLaterApprovedExcuseTurnsARecordedAbsenceIntoExcusedWithoutAnEvent() {
    call(
        "PUT",
        "/api/v1/attendance-replicas/guardian-links/" + ali,
        admin(),
        Map.of("guardianId", alisGuardian),
        null);
    UUID excuseId =
        UUID.fromString(
            call(
                    "POST",
                    "/api/v1/excuses",
                    guardian(alisGuardian),
                    Map.of(
                        "studentId",
                        ali,
                        "fromDate",
                        today.toString(),
                        "toDate",
                        today.toString(),
                        "reason",
                        "Flu"),
                    null)
                .getBody()
                .get("id")
                .asString());

    call("POST", "/api/v1/excuses/" + excuseId + "/approve", admin(), null, null);

    JsonNode own = call("GET", "/api/v1/attendance-records", student(ali), null, null).getBody();
    assertThat(own.get("content").get(0).get("status").asString()).isEqualTo("EXCUSED");
    assertThat(own.get("content").get(0).get("excuseId").asString()).isEqualTo(excuseId.toString());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from attendance_record_change where reason = 'Excuse approved'",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  @Order(10)
  void eventsReachConsumersMatchingTheirSchemas() throws IOException {
    // Taking: absent Ali, teacher-excused Can. Correction back to ABSENT: absent Ali again.
    // Nothing for Ayse (approved excuse), the correction to PRESENT or the later excuse approval.
    List<Received> received = eventQueue().receive(3, Duration.ofSeconds(30));

    assertThat(received)
        .extracting(Received::eventType)
        .containsExactlyInAnyOrder("StudentAbsent", "StudentAbsent", "StudentExcusedByTeacher");
    assertThat(received)
        .filteredOn(r -> r.eventType().equals("StudentAbsent"))
        .extracting(r -> r.data().get("studentId").asString())
        .containsOnly(ali.toString());
    assertThat(received)
        .filteredOn(r -> r.eventType().equals("StudentExcusedByTeacher"))
        .extracting(r -> r.data().get("studentId").asString())
        .containsExactly(can.toString());
    for (Received event : received) {
      assertThat(event.attributes()).containsEntry("eventType", event.eventType());
      assertThat(event.data().get("sessionId").asString()).isEqualTo(sessionId.toString());
      assertThat(event.data().get("sessionDate").asString()).isEqualTo(today.toString());
      assertMatchesSchema("envelope.schema.json", event.envelope());
      assertMatchesSchema("attendance/" + event.eventType() + ".schema.json", event.data());
    }
    assertThat(eventQueue().receive(1, Duration.ofSeconds(3))).as("no further events").isEmpty();
  }

  @Test
  @Order(11)
  void theJobsGenerateSessionsOnceAndExpireElapsedOnes() {
    UUID classroom = UUID.randomUUID();
    ResponseEntity<JsonNode> schedule =
        call(
            "POST",
            "/api/v1/class-schedules",
            admin(),
            Map.of(
                "branchId",
                branchId,
                "classroomId",
                classroom,
                "teacherId",
                UUID.randomUUID(),
                "subjectId",
                subjectId,
                "dayOfWeek",
                today.getDayOfWeek().name(),
                "startsAt",
                "14:00",
                "endsAt",
                "14:40",
                "validFrom",
                today.toString()),
            null);
    assertThat(schedule.getStatusCode().value()).isEqualTo(201);
    UUID scheduleId = UUID.fromString(schedule.getBody().get("id").asString());

    GenerateSessionsResult first = generateSessions.generate(7);
    GenerateSessionsResult second = generateSessions.generate(7);

    // Today and the same weekday one week later.
    assertThat(first.created()).isEqualTo(2);
    assertThat(first.failed()).isZero();
    assertThat(second.created()).isZero();
    assertThat(second.alreadyExisting()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from class_session where schedule_id = ?", Long.class, scheduleId))
        .isEqualTo(2L);

    UUID elapsed = UUID.randomUUID();
    jdbc.update(
        "insert into class_session (id, branch_id, classroom_id, teacher_id, subject_id,"
            + " session_date, starts_at, ends_at, status, created_at, updated_at, version)"
            + " values (?, ?, ?, ?, ?, ?, '09:00', '09:40', 'PLANNED', now(), now(), 0)",
        elapsed,
        branchId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        subjectId,
        today.minusDays(2));

    assertThat(expireSessions.expireElapsed()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select status from class_session where id = ?", String.class, elapsed))
        .isEqualTo("EXPIRED");
    assertThat(expireSessions.expireElapsed()).isZero();
  }

  @Test
  @Order(12)
  void operationalEndpointsExposeHealthMetricsAndBuildInfo() {
    RestClient management = client("http://localhost:" + managementPort);
    String metrics = management.get().uri("/metrics").retrieve().body(String.class);
    String readiness = management.get().uri("/health/readiness").retrieve().body(String.class);
    String info = management.get().uri("/info").retrieve().body(String.class);

    assertThat(readiness).contains("UP");
    assertThat(metrics)
        .contains("attendance_submissions_total{application=\"attendance\",result=\"taken\"")
        .contains("attendance_absences_published_total")
        .contains("attendance_excused_published_total")
        .contains("attendance_corrections_total")
        .contains("attendance_sessions_total")
        .contains("outbox_events_published_total")
        .contains("business_rule_violations_total")
        .contains("http_server_requests_seconds");
    assertThat(info).contains("it-commit");
  }

  @Test
  @Order(13)
  void openApiDocumentIsExportedForTheFrontend() throws IOException {
    String document =
        client("http://localhost:" + port).get().uri("/v3/api-docs").retrieve().body(String.class);

    Path output = Path.of("target", "openapi", "openapi.json");
    Files.createDirectories(output.getParent());
    Files.writeString(output, document);
    assertThat(document)
        .contains("/api/v1/class-sessions/{sessionId}/attendance")
        .contains("/api/v1/excuses")
        .contains("Idempotency-Key");
  }

  private Map<String, Object> sessionBody(UUID classroom, UUID teacher, int hour) {
    return Map.of(
        "branchId", branchId,
        "classroomId", classroom,
        "teacherId", teacher,
        "subjectId", subjectId,
        "sessionDate", today.toString(),
        "startsAt", "%02d:00".formatted(hour),
        "endsAt", "%02d:40".formatted(hour));
  }

  private static Map<String, Object> entry(UUID studentId, String status) {
    return Map.of("studentId", studentId, "status", status);
  }

  private static JsonNode rowOf(JsonNode students, UUID studentId) {
    for (JsonNode row : students) {
      if (row.get("studentId").asString().equals(studentId.toString())) {
        return row;
      }
    }
    throw new AssertionError("Student " + studentId + " is not on the roster");
  }

  private String rosterPath() {
    return "/api/v1/class-sessions/" + sessionId + "/roster";
  }

  private String attendancePath() {
    return "/api/v1/class-sessions/" + sessionId + "/attendance";
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
