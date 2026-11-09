package com.dershane.messagingservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.dershane.messagingservice.shared.application.MetricNames;
import com.dershane.messagingservice.support.EventQueue;
import com.dershane.messagingservice.support.TestContainers;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import tools.jackson.databind.json.JsonMapper;

/**
 * The user service's {@code UserUpdated} events travel through Floci (SNS topic → SQS queue, as in
 * infra/terraform) to the running application, which keeps its copy of students and teachers in
 * step. The event is built like the user service builds it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserEventsInboxIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static EventQueue userEvents;

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
    registry.add("messaging.outbox.enabled", () -> "false");
    registry.add("messaging.inbox.queue-name", () -> userEvents().queueName());
    registry.add("messaging.inbox.wait-time", () -> "1s");
    registry.add("management.server.port", () -> "0");
    registry.add("GIT_COMMIT_SHA", () -> "it-commit");
  }

  /** Stands in for the user service's topic and the messaging queue subscribed to it. */
  private static synchronized EventQueue userEvents() {
    if (userEvents == null) {
      userEvents = EventQueue.create(TestContainers.floci());
    }
    return userEvents;
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private MeterRegistry meters;

  private final UUID userId = UUID.randomUUID();
  private final UUID branchId = UUID.randomUUID();

  private Map<String, Object> user(String role, String firstName, long recordVersion) {
    Map<String, Object> data = new HashMap<>();
    data.put("userId", userId);
    data.put("role", role);
    data.put("branchId", branchId);
    data.put("firstName", firstName);
    data.put("lastName", "Tester");
    data.put("email", null);
    data.put("phone", null);
    data.put("active", true);
    data.put("studentNumber", null);
    data.put("recordVersion", recordVersion);
    return data;
  }

  @Test
  void aStudentIsCopiedAndANewerEventUpdatesIt() {
    publishAndAwait(user("STUDENT", "Ada", 1));
    assertThat(firstName()).isEqualTo("Ada");

    publishAndAwait(user("STUDENT", "Adalet", 2));
    assertThat(firstName()).isEqualTo("Adalet");
  }

  @Test
  void aStaleEventDoesNotOverwriteNewerData() {
    publishAndAwait(user("TEACHER", "Newer", 5));
    publishAndAwait(user("TEACHER", "Older", 4));

    assertThat(firstName()).isEqualTo("Newer");
  }

  @Test
  void guardiansAreNotCopied() {
    publishAndAwait(user("GUARDIAN", "Parent", 1));

    assertThat(
            jdbc.queryForObject(
                "select count(*) from user_replica where user_id = ?", Integer.class, userId))
        .isZero();
  }

  @Test
  void aRepeatedDeliveryIsSkipped() {
    UUID eventId = UUID.randomUUID();
    Map<String, Object> data = user("STUDENT", "Twice", 1);
    double duplicatesBefore = consumed("UserUpdated", "duplicate");

    publish(eventId, data);
    publish(eventId, data);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> assertThat(consumed("UserUpdated", "duplicate")).isEqualTo(duplicatesBefore + 1));
    assertThat(count("processed_event where event_id = ?", eventId)).isEqualTo(1);
  }

  @Test
  void aBrokenEventLeavesNoTraceAndStaysInTheQueue() {
    UUID eventId = UUID.randomUUID();
    double failedBefore = consumed("UserUpdated", "failed");
    Map<String, Object> data = user("STUDENT", "Broken", 1);
    data.put("branchId", "not-a-uuid");

    publish(eventId, data);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> assertThat(consumed("UserUpdated", "failed")).isGreaterThan(failedBefore));
    assertThat(count("processed_event where event_id = ?", eventId))
        .as("the processed marker is rolled back with the failed change")
        .isZero();
  }

  private void publishAndAwait(Map<String, Object> data) {
    UUID eventId = UUID.randomUUID();
    publish(eventId, data);
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(count("processed_event where event_id = ?", eventId))
                    .as("UserUpdated %s processed", eventId)
                    .isEqualTo(1));
  }

  private void publish(UUID eventId, Map<String, Object> data) {
    String message =
        JSON.writeValueAsString(
            Map.of(
                "eventId",
                eventId,
                "eventType",
                "UserUpdated",
                "schemaVersion",
                1,
                "occurredAt",
                Instant.now().toString(),
                "source",
                "user",
                "data",
                data));
    userEvents()
        .snsClient()
        .publish(
            request ->
                request
                    .topicArn(userEvents().topicArn())
                    .message(message)
                    .messageAttributes(
                        Map.of(
                            "eventType",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue("UserUpdated")
                                .build())));
  }

  private String firstName() {
    return jdbc.queryForObject(
        "select first_name from user_replica where user_id = ?", String.class, userId);
  }

  private int count(String fromAndWhere, UUID id) {
    return jdbc.queryForObject("select count(*) from " + fromAndWhere, Integer.class, id);
  }

  private double consumed(String eventType, String result) {
    return meters
        .counter(MetricNames.EVENTS_CONSUMED, "eventType", eventType, "result", result)
        .count();
  }
}
