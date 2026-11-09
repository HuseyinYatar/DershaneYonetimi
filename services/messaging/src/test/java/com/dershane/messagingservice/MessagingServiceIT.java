package com.dershane.messagingservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.dershane.messagingservice.support.EventQueue;
import com.dershane.messagingservice.support.TestContainers;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;

/**
 * End to end against real PostgreSQL, Redis and Floci: the sequence counter under concurrency, the
 * client sync query, the one-time WebSocket ticket, live frames, the NewMessage event for offline
 * recipients, and the report-based audit. Only token validation is replaced: the JwtDecoder
 * resolves test token strings to Keycloak-shaped JWTs, so the rest of the security chain runs
 * unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MessagingServiceIT {

  private static EventQueue events;

  @DynamicPropertySource
  static void environment(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestContainers.postgres().getJdbcUrl());
    registry.add("spring.datasource.username", () -> TestContainers.postgres().getUsername());
    registry.add("spring.datasource.password", () -> TestContainers.postgres().getPassword());
    registry.add("spring.data.redis.host", () -> TestContainers.redis().getHost());
    registry.add("spring.data.redis.port", () -> TestContainers.redis().getMappedPort(6379));
    registry.add("messaging.aws.region", () -> TestContainers.REGION);
    registry.add("messaging.aws.endpoint-url", () -> TestContainers.floci().getEndpoint());
    registry.add("messaging.aws.static-access-key-id", () -> TestContainers.floci().getAccessKey());
    registry.add(
        "messaging.aws.static-secret-access-key", () -> TestContainers.floci().getSecretKey());
    registry.add("messaging.outbox.topic-arn", () -> eventQueue().topicArn());
    registry.add("messaging.outbox.publish-interval", () -> "200ms");
    // Users are inserted directly here; UserEventsInboxIT covers the queue.
    registry.add("messaging.inbox.enabled", () -> "false");
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
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StringRedisTemplate redis;
  @LocalServerPort private int port;

  private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
  private final UUID branchId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();

  @BeforeEach
  void resolveTestTokens() {
    when(jwtDecoder.decode(anyString()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(tokens.get(invocation.<String>getArgument(0)))
                    .orElseThrow(() -> new BadJwtException("Unknown test token")));
    addUser(studentId, "STUDENT", branchId, "Ada");
    addUser(teacherId, "TEACHER", branchId, "Berk");
  }

  // ---------------------------------------------------------------- conversations

  @Test
  void openingTheSameConversationTwiceReturnsTheSameOne() {
    ResponseEntity<JsonNode> first = open(student(), teacherId);
    ResponseEntity<JsonNode> second = open(teacher(), studentId);

    assertThat(first.getStatusCode().value()).isEqualTo(201);
    assertThat(second.getStatusCode().value()).isEqualTo(200);
    assertThat(second.getBody().get("id").asString())
        .isEqualTo(first.getBody().get("id").asString());
    assertThat(first.getBody().get("otherParticipant").get("firstName").asString())
        .isEqualTo("Berk");
  }

  @Test
  void aStudentCannotMessageAStudentOrAnotherBranch() {
    UUID otherStudent = UUID.randomUUID();
    UUID faraway = UUID.randomUUID();
    addUser(otherStudent, "STUDENT", branchId, "Cem");
    addUser(faraway, "TEACHER", UUID.randomUUID(), "Dilek");

    ResponseEntity<JsonNode> sameRole = open(student(), otherStudent);
    ResponseEntity<JsonNode> otherBranch = open(student(), faraway);
    ResponseEntity<JsonNode> unknown = open(student(), UUID.randomUUID());

    assertThat(sameRole.getStatusCode().value()).isEqualTo(403);
    assertThat(sameRole.getBody().get("errorCode").asString())
        .isEqualTo("CONVERSATION_NOT_ALLOWED");
    assertThat(otherBranch.getStatusCode().value()).isEqualTo(403);
    assertThat(unknown.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void aThirdPersonCannotReadOrWriteAConversation() {
    String conversationId = conversation();
    String stranger = token(UUID.randomUUID(), "student");

    assertThat(
            call(
                    "GET",
                    "/api/v1/conversations/" + conversationId + "/messages",
                    stranger,
                    null,
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(403);
    assertThat(send(stranger, conversationId, "hi").getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void aRepeatedSendWithTheSameIdempotencyKeyCreatesOneMessage() {
    String conversationId = conversation();
    Map<String, Object> body = Map.of("type", "TEXT", "content", "once");

    ResponseEntity<JsonNode> first =
        call(
            "POST",
            "/api/v1/conversations/" + conversationId + "/messages",
            student(),
            body,
            "key-1");
    ResponseEntity<JsonNode> second =
        call(
            "POST",
            "/api/v1/conversations/" + conversationId + "/messages",
            student(),
            body,
            "key-1");

    assertThat(second.getBody().get("id").asString())
        .isEqualTo(first.getBody().get("id").asString());
    assertThat(messages(conversationId, "")).hasSize(1);
  }

  // ---------------------------------------------------------------- sequence and sync

  @Test
  void parallelSendsGetGaplessUniqueNumbers() throws Exception {
    String conversationId = conversation();
    int count = 20;

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      List<Future<ResponseEntity<JsonNode>>> results = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        int n = i;
        results.add(
            pool.submit(() -> send(n % 2 == 0 ? student() : teacher(), conversationId, "m" + n)));
      }
      for (Future<ResponseEntity<JsonNode>> result : results) {
        assertThat(result.get().getStatusCode().value()).isEqualTo(201);
      }
    }

    List<Long> seqs =
        messages(conversationId, "?limit=200").stream().map(m -> m.get("seq").asLong()).toList();
    assertThat(seqs)
        .containsExactlyElementsOf(
            java.util.stream.LongStream.rangeClosed(1, count).boxed().toList());
  }

  @Test
  void theSyncQueryReturnsNewEditedAndDeletedMessagesAndPages() throws Exception {
    String conversationId = conversation();
    String m1 = send(student(), conversationId, "one").getBody().get("id").asString();
    String m2 = send(student(), conversationId, "two").getBody().get("id").asString();
    String m3 = send(student(), conversationId, "three").getBody().get("id").asString();
    Thread.sleep(100);
    Instant mark = Instant.now();
    Thread.sleep(100);

    ResponseEntity<JsonNode> edited =
        call(
            "PATCH",
            "/api/v1/conversations/" + conversationId + "/messages/" + m2,
            student(),
            Map.of("content", "two edited"),
            null);
    ResponseEntity<JsonNode> deleted =
        call(
            "DELETE",
            "/api/v1/conversations/" + conversationId + "/messages/" + m3,
            student(),
            null,
            null);
    String m4 = send(teacher(), conversationId, "four").getBody().get("id").asString();

    assertThat(edited.getStatusCode().value()).isEqualTo(200);
    assertThat(deleted.getStatusCode().value()).isEqualTo(204);

    List<JsonNode> sync = messages(conversationId, "?afterSeq=3&updatedSince=" + mark);
    assertThat(sync.stream().map(m -> m.get("id").asString()).toList()).containsExactly(m2, m3, m4);
    assertThat(sync.get(0).get("content").asString()).isEqualTo("two edited");
    assertThat(sync.get(0).get("editedAt").isNull()).isFalse();
    assertThat(sync.get(1).get("deleted").asBoolean()).isTrue();
    assertThat(sync.get(1).get("content").isNull()).isTrue();

    assertThat(
            messages(conversationId, "?afterSeq=3").stream()
                .map(m -> m.get("id").asString())
                .toList())
        .containsExactly(m4);

    ResponseEntity<JsonNode> page =
        call(
            "GET",
            "/api/v1/conversations/" + conversationId + "/messages?beforeSeq=4&limit=2",
            student(),
            null,
            null);
    assertThat(page.getBody().get("hasMore").asBoolean()).isTrue();
    assertThat(page.getBody().get("messages").get(0).get("id").asString()).isEqualTo(m2);
    assertThat(page.getBody().get("messages").get(1).get("id").asString()).isEqualTo(m3);
    assertThat(m1).isNotBlank();
  }

  @Test
  void aTeacherCannotEditAStudentsMessageAndEditsKeepTheOldText() {
    String conversationId = conversation();
    String id = send(student(), conversationId, "original").getBody().get("id").asString();

    ResponseEntity<JsonNode> foreign =
        call(
            "PATCH",
            "/api/v1/conversations/" + conversationId + "/messages/" + id,
            teacher(),
            Map.of("content", "hijack"),
            null);
    call(
        "PATCH",
        "/api/v1/conversations/" + conversationId + "/messages/" + id,
        student(),
        Map.of("content", "changed"),
        null);

    assertThat(foreign.getStatusCode().value()).isEqualTo(403);
    assertThat(foreign.getBody().get("errorCode").asString()).isEqualTo("NOT_MESSAGE_SENDER");
    assertThat(
            jdbc.queryForObject(
                "select previous_content from message_edit where message_id = ?",
                String.class,
                UUID.fromString(id)))
        .isEqualTo("original");
  }

  @Test
  void readPositionsAndUnreadCountsFollowTheMessages() {
    String conversationId = conversation();
    send(student(), conversationId, "a");
    send(student(), conversationId, "b");

    JsonNode before =
        call("GET", "/api/v1/conversations/" + conversationId, teacher(), null, null).getBody();
    ResponseEntity<JsonNode> tooFar =
        call(
            "PUT",
            "/api/v1/conversations/" + conversationId + "/read",
            teacher(),
            Map.of("seq", 9),
            null);
    ResponseEntity<JsonNode> ok =
        call(
            "PUT",
            "/api/v1/conversations/" + conversationId + "/read",
            teacher(),
            Map.of("seq", 1),
            null);
    JsonNode after =
        call("GET", "/api/v1/conversations/" + conversationId, teacher(), null, null).getBody();
    JsonNode senderView =
        call("GET", "/api/v1/conversations/" + conversationId, student(), null, null).getBody();

    assertThat(before.get("unreadCount").asLong()).isEqualTo(2);
    assertThat(tooFar.getStatusCode().value()).isEqualTo(422);
    assertThat(tooFar.getBody().get("errorCode").asString()).isEqualTo("READ_SEQ_OUT_OF_RANGE");
    assertThat(ok.getStatusCode().value()).isEqualTo(204);
    assertThat(after.get("unreadCount").asLong()).isEqualTo(1);
    assertThat(senderView.get("lastReadSeq").asLong())
        .as("the sender has read their own")
        .isEqualTo(2);
  }

  // ---------------------------------------------------------------- NewMessage event

  @Test
  void everyMessageToAnOfflineRecipientWritesANewMessageEvent() {
    String conversationId = conversation();

    send(student(), conversationId, "one");
    send(student(), conversationId, "two");
    assertThat(newMessageEvents(conversationId)).isEqualTo(2);

    redis.opsForValue().set("presence:" + teacherId, "1", Duration.ofSeconds(60));
    send(student(), conversationId, "three");
    assertThat(newMessageEvents(conversationId)).as("the teacher is online").isEqualTo(2);

    send(teacher(), conversationId, "four");
    assertThat(newMessageEvents(conversationId)).as("the student is offline").isEqualTo(3);
    redis.delete("presence:" + teacherId);
  }

  private int newMessageEvents(String conversationId) {
    return jdbc.queryForObject(
        "select count(*) from outbox_event where event_type = 'NewMessage'"
            + " and payload->'data'->>'conversationId' = ?",
        Integer.class,
        conversationId);
  }

  // ---------------------------------------------------------------- realtime

  @Test
  void aTicketWorksOnceAndFramesReachBothConnectedSides() throws Exception {
    String conversationId = conversation();
    BlockingQueue<String> studentFrames = new LinkedBlockingQueue<>();
    BlockingQueue<String> teacherFrames = new LinkedBlockingQueue<>();

    String studentTicket = ticket(student());
    WebSocketSession studentSocket = connect(studentTicket, studentFrames);
    WebSocketSession teacherSocket = connect(ticket(teacher()), teacherFrames);

    assertThatThrownBy(() -> connect(studentTicket, new LinkedBlockingQueue<>()))
        .as("a ticket is single use")
        .isInstanceOf(Exception.class);
    assertThatThrownBy(() -> connect("not-a-ticket", new LinkedBlockingQueue<>()))
        .isInstanceOf(Exception.class);
    assertThat(redis.hasKey("presence:" + studentId)).isTrue();

    String id = send(teacher(), conversationId, "hello student").getBody().get("id").asString();
    JsonNode toStudent = frame(studentFrames);
    JsonNode toTeacher = frame(teacherFrames);
    assertThat(toStudent.get("type").asString()).isEqualTo("MESSAGE");
    assertThat(toStudent.get("message").get("id").asString()).isEqualTo(id);
    assertThat(toStudent.get("message").get("content").asString()).isEqualTo("hello student");
    assertThat(toTeacher.get("message").get("id").asString()).isEqualTo(id);

    call(
        "PUT",
        "/api/v1/conversations/" + conversationId + "/read",
        student(),
        Map.of("seq", 1),
        null);
    JsonNode read = frame(teacherFrames);
    assertThat(read.get("type").asString()).isEqualTo("READ");
    assertThat(read.get("userId").asString()).isEqualTo(studentId.toString());
    assertThat(read.get("lastReadSeq").asLong()).isEqualTo(1);

    studentSocket.sendMessage(new TextMessage("{\"type\":\"PING\"}"));
    boolean pong = false;
    for (int i = 0; i < 5 && !pong; i++) {
      JsonNode f = frame(studentFrames);
      pong = "PONG".equals(f.get("type").asString());
    }
    assertThat(pong).isTrue();

    studentSocket.close();
    teacherSocket.close();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(redis.hasKey("presence:" + studentId)).isFalse());
  }

  @Test
  void onlyStudentsAndTeachersGetTickets() {
    String guardian = token(UUID.randomUUID(), "guardian");

    assertThat(call("POST", "/api/v1/chat/tickets", guardian, null, null).getStatusCode().value())
        .isEqualTo(403);
    assertThat(call("POST", "/api/v1/chat/tickets", student(), null, null).getStatusCode().value())
        .isEqualTo(200);
  }

  private String ticket(String token) {
    return call("POST", "/api/v1/chat/tickets", token, null, null)
        .getBody()
        .get("ticket")
        .asString();
  }

  private WebSocketSession connect(String ticket, BlockingQueue<String> frames) throws Exception {
    TextWebSocketHandler handler =
        new TextWebSocketHandler() {
          @Override
          protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
          }

          @Override
          public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            frames.add("{\"type\":\"CLOSED\"}");
          }
        };
    return new StandardWebSocketClient()
        .execute(handler, "ws://localhost:" + port + "/api/v1/chat/ws?ticket=" + ticket)
        .get(10, TimeUnit.SECONDS);
  }

  private JsonNode frame(BlockingQueue<String> frames) throws Exception {
    String payload = frames.poll(10, TimeUnit.SECONDS);
    assertThat(payload).as("a frame arrives").isNotNull();
    return new tools.jackson.databind.json.JsonMapper().readTree(payload);
  }

  // ---------------------------------------------------------------- reports and audit

  private record Reported(String conversationId, String deletedMessageId) {}

  /** A conversation with a deleted message (seq 1), a reply and an edited message (seq 3). */
  private Reported conversationWithDeletedAndEditedMessages() {
    String conversationId = conversation();
    String id = send(teacher(), conversationId, "bad words").getBody().get("id").asString();
    send(student(), conversationId, "reply");
    String edited = send(teacher(), conversationId, "first draft").getBody().get("id").asString();
    call(
        "PATCH",
        "/api/v1/conversations/" + conversationId + "/messages/" + edited,
        teacher(),
        Map.of("content", "second draft"),
        null);
    call(
        "DELETE",
        "/api/v1/conversations/" + conversationId + "/messages/" + id,
        teacher(),
        null,
        null);

    return new Reported(conversationId, id);
  }

  @Test
  void reportingIsLimitedToParticipantsOnceAndNotForOwnMessages() {
    String id = conversationWithDeletedAndEditedMessages().deletedMessageId();
    String otherBranchAdmin = token(UUID.randomUUID(), "admin", UUID.randomUUID());

    ResponseEntity<JsonNode> ownMessage =
        call(
            "POST",
            "/api/v1/message-reports",
            teacher(),
            Map.of("messageId", id, "reason", "x"),
            null);
    ResponseEntity<JsonNode> report =
        call(
            "POST",
            "/api/v1/message-reports",
            student(),
            Map.of("messageId", id, "reason", "rude"),
            null);
    ResponseEntity<JsonNode> again =
        call(
            "POST",
            "/api/v1/message-reports",
            student(),
            Map.of("messageId", id, "reason", "rude"),
            null);
    String reportId = report.getBody().get("id").asString();

    assertThat(ownMessage.getBody().get("errorCode").asString()).isEqualTo("OWN_MESSAGE_REPORT");
    assertThat(report.getStatusCode().value()).isEqualTo(201);
    assertThat(again.getBody().get("errorCode").asString()).isEqualTo("REPORT_ALREADY_EXISTS");
    assertThat(
            call("GET", "/api/v1/message-reports", teacher(), null, null).getStatusCode().value())
        .as("a teacher cannot list reports")
        .isEqualTo(403);
    assertThat(
            call("GET", "/api/v1/message-reports/" + reportId, otherBranchAdmin, null, null)
                .getBody()
                .get("errorCode")
                .asString())
        .isEqualTo("BRANCH_SCOPE_VIOLATION");
  }

  @Test
  void theBranchAdminReadsTheReportedConversationAndClosesTheReport() {
    String id = conversationWithDeletedAndEditedMessages().deletedMessageId();
    String admin = token(adminId, "admin");
    String reportId =
        call(
                "POST",
                "/api/v1/message-reports",
                student(),
                Map.of("messageId", id, "reason", "rude"),
                null)
            .getBody()
            .get("id")
            .asString();

    JsonNode list = call("GET", "/api/v1/message-reports?status=OPEN", admin, null, null).getBody();
    assertThat(list.get("content").size()).isGreaterThanOrEqualTo(1);

    JsonNode audit =
        call("GET", "/api/v1/message-reports/" + reportId + "/messages", admin, null, null)
            .getBody();
    JsonNode deletedMessage = audit.get(0);
    assertThat(deletedMessage.get("deleted").asBoolean()).isTrue();
    assertThat(deletedMessage.get("content").asString())
        .as("deleted content is visible to audit")
        .isEqualTo("bad words");
    JsonNode editedMessage = audit.get(2);
    assertThat(editedMessage.get("edits").get(0).get("previousContent").asString())
        .isEqualTo("first draft");

    JsonNode one = call("GET", "/api/v1/message-reports/" + reportId, admin, null, null).getBody();
    assertThat(one.get("message").get("content").asString()).isEqualTo("bad words");
    assertThat(one.get("reporter").get("firstName").asString()).isEqualTo("Ada");

    assertThat(
            call(
                    "POST",
                    "/api/v1/message-reports/" + reportId + "/close",
                    admin,
                    Map.of("resolutionNote", "talked"),
                    null)
                .getBody()
                .get("status")
                .asString())
        .isEqualTo("CLOSED");
    assertThat(
            call("POST", "/api/v1/message-reports/" + reportId + "/close", admin, Map.of(), null)
                .getBody()
                .get("errorCode")
                .asString())
        .isEqualTo("REPORT_ALREADY_CLOSED");
  }

  // ---------------------------------------------------------------- helpers

  private void addUser(UUID id, String role, UUID branch, String firstName) {
    jdbc.update(
        """
        insert into user_replica (user_id, role, branch_id, first_name, last_name, active,
                                  record_version, created_at, updated_at, version)
        values (?, ?, ?, ?, 'Tester', true, 1, now(), now(), 0)
        on conflict (user_id) do nothing
        """,
        id,
        role,
        branch,
        firstName);
  }

  /** A fresh student–teacher pair so tests do not share a message sequence. */
  private String conversation() {
    return open(student(), teacherId).getBody().get("id").asString();
  }

  private ResponseEntity<JsonNode> open(String token, UUID participantId) {
    return call(
        "POST", "/api/v1/conversations", token, Map.of("participantId", participantId), null);
  }

  private ResponseEntity<JsonNode> send(String token, String conversationId, String text) {
    return call(
        "POST",
        "/api/v1/conversations/" + conversationId + "/messages",
        token,
        Map.of("type", "TEXT", "content", text),
        null);
  }

  private List<JsonNode> messages(String conversationId, String query) {
    JsonNode body =
        call(
                "GET",
                "/api/v1/conversations/" + conversationId + "/messages" + query,
                student(),
                null,
                null)
            .getBody();
    List<JsonNode> result = new ArrayList<>();
    body.get("messages").forEach(result::add);
    return result;
  }

  private ResponseEntity<JsonNode> call(
      String method, String path, String token, Object body, String idempotencyKey) {
    RestClient.RequestBodySpec request =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (req, response) -> {})
            .build()
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

  private String student() {
    return token(studentId, "student");
  }

  private String teacher() {
    return token(teacherId, "teacher");
  }

  private String token(UUID userId, String role) {
    return token(userId, role, branchId);
  }

  private String token(UUID userId, String role, UUID branch) {
    String value = userId + "." + role;
    tokens.put(
        value,
        Jwt.withTokenValue(value)
            .header("alg", "none")
            .subject(userId.toString())
            .claim("branch_id", branch.toString())
            .claim("realm_access", Map.of("roles", List.of(role)))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build());
    return value;
  }
}
