package com.dershane.taskservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.dershane.taskservice.shared.application.MetricNames;
import com.dershane.taskservice.support.EventQueue;
import com.dershane.taskservice.support.TestContainers;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * The user service's events travel through Floci (SNS topic → SQS queue, as in infra/terraform) to
 * the running application, which keeps its local copies of memberships and guardian links in step.
 * The events are built like the user service builds them; the schemas are checked in {@code
 * UserEventHandlerTest}.
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
    registry.add("task.aws.region", () -> TestContainers.REGION);
    registry.add("task.aws.endpoint-url", () -> TestContainers.floci().getEndpoint());
    registry.add("task.aws.static-access-key-id", () -> TestContainers.floci().getAccessKey());
    registry.add("task.aws.static-secret-access-key", () -> TestContainers.floci().getSecretKey());
    registry.add("task.outbox.enabled", () -> "false");
    registry.add("task.inbox.queue-name", () -> userEvents().queueName());
    registry.add("task.inbox.wait-time", () -> "1s");
    registry.add("management.server.port", () -> "0");
    registry.add("GIT_COMMIT_SHA", () -> "it-commit");
  }

  /** Stands in for the user service's topic and the task queue subscribed to it. */
  private static synchronized EventQueue userEvents() {
    if (userEvents == null) {
      userEvents = EventQueue.create(TestContainers.floci());
    }
    return userEvents;
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private MeterRegistry meters;

  private final UUID studentId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();
  private final UUID branchId = UUID.randomUUID();

  @Test
  void membershipEventsOpenAndCloseTheLocalCopy() {
    UUID membershipId = UUID.randomUUID();

    publishAndAwait(
        "StudentAddedToClassroom",
        Map.of(
            "membershipId", membershipId,
            "studentId", studentId,
            "classroomId", classroomId,
            "branchId", branchId,
            "studentNumber", "ST1",
            "fullName", "Student One",
            "startDate", "2026-09-14"));
    assertThat(endDateOf(membershipId)).isNull();

    publishAndAwait(
        "StudentRemovedFromClassroom",
        Map.of(
            "membershipId", membershipId,
            "studentId", studentId,
            "classroomId", classroomId,
            "branchId", branchId,
            "endDate", "2026-10-30"));
    assertThat(endDateOf(membershipId)).isEqualTo(LocalDate.of(2026, 10, 30));
  }

  @Test
  void aRepeatedDeliveryIsSkipped() {
    UUID membershipId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    Map<String, Object> data =
        Map.of(
            "membershipId", membershipId,
            "studentId", studentId,
            "classroomId", classroomId,
            "branchId", branchId,
            "studentNumber", "ST2",
            "fullName", "Student Two",
            "startDate", "2026-09-14");
    double duplicatesBefore = consumed("StudentAddedToClassroom", "duplicate");

    publish(eventId, "StudentAddedToClassroom", data);
    publish(eventId, "StudentAddedToClassroom", data);

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(consumed("StudentAddedToClassroom", "duplicate"))
                    .isEqualTo(duplicatesBefore + 1));
    assertThat(count("membership_replica where id = ?", membershipId)).isEqualTo(1);
    assertThat(count("processed_event where event_id = ?", eventId)).isEqualTo(1);
  }

  @Test
  void aLateUnlinkOfAReplacedGuardianKeepsTheNewLink() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    publishAndAwait("GuardianLinked", link(first));
    publishAndAwait("GuardianLinked", link(second));
    // The user service sends this before the second "linked"; queues may reorder them.
    publishAndAwait("GuardianUnlinked", Map.of("guardianId", first, "studentId", studentId));

    assertThat(guardianOf(studentId)).containsExactly(second);

    publishAndAwait("GuardianUnlinked", Map.of("guardianId", second, "studentId", studentId));

    assertThat(guardianOf(studentId)).isEmpty();
  }

  @Test
  void aFailedEventLeavesNoTraceAndStaysInTheQueue() {
    UUID eventId = UUID.randomUUID();
    double failedBefore = consumed("StudentRemovedFromClassroom", "failed");

    // The membership was never recorded, so closing it is refused (MEMBERSHIP_NOT_FOUND).
    publish(
        eventId,
        "StudentRemovedFromClassroom",
        Map.of(
            "membershipId", UUID.randomUUID(),
            "studentId", studentId,
            "classroomId", classroomId,
            "branchId", branchId,
            "endDate", "2026-10-30"));

    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(consumed("StudentRemovedFromClassroom", "failed"))
                    .isGreaterThan(failedBefore));
    assertThat(count("processed_event where event_id = ?", eventId))
        .as("the processed marker is rolled back with the failed change")
        .isZero();
  }

  @Test
  void joiningAndLeavingAClassroomHandsOutAndCancelsAssignments() {
    UUID taskId = UUID.randomUUID();
    LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
    jdbc.update(
        """
        insert into task (id, branch_id, teacher_id, title, description, target_type, target_id,
                          recurrence, recurrence_days, start_date, end_date, due_time,
                          attachment_file_ids, active, created_at, updated_at, version)
        values (?, ?, ?, 'Daily problems', '', 'CLASSROOM', ?, 'DAILY', '{}', ?, null, '23:59',
                '{}', true, now(), now(), 0)
        """,
        taskId,
        branchId,
        UUID.randomUUID(),
        classroomId,
        today.minusDays(1));
    UUID membershipId = UUID.randomUUID();

    publishAndAwait(
        "StudentAddedToClassroom",
        Map.of(
            "membershipId", membershipId,
            "studentId", studentId,
            "classroomId", classroomId,
            "branchId", branchId,
            "studentNumber", "ST3",
            "fullName", "Student Three",
            "startDate", today.toString()));

    assertThat(count("assignment where student_id = ? and status = 'ASSIGNED'", studentId))
        .isEqualTo(1);

    publishAndAwait(
        "StudentRemovedFromClassroom",
        Map.of(
            "membershipId", membershipId,
            "studentId", studentId,
            "classroomId", classroomId,
            "branchId", branchId,
            "endDate", today.toString()));

    assertThat(count("assignment where student_id = ? and status = 'CANCELLED'", studentId))
        .isEqualTo(1);
  }

  private Map<String, Object> link(UUID guardianId) {
    return Map.of("guardianId", guardianId, "studentId", studentId, "relationship", "MOTHER");
  }

  private void publishAndAwait(String eventType, Map<String, Object> data) {
    UUID eventId = UUID.randomUUID();
    publish(eventId, eventType, data);
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(count("processed_event where event_id = ?", eventId))
                    .as("%s %s processed", eventType, eventId)
                    .isEqualTo(1));
  }

  private void publish(UUID eventId, String eventType, Map<String, Object> data) {
    String message =
        JSON.writeValueAsString(
            Map.of(
                "eventId",
                eventId,
                "eventType",
                eventType,
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
                                .stringValue(eventType)
                                .build())));
  }

  private LocalDate endDateOf(UUID membershipId) {
    return jdbc.queryForObject(
        "select end_date from membership_replica where id = ?", LocalDate.class, membershipId);
  }

  private List<UUID> guardianOf(UUID student) {
    return jdbc.queryForList(
        "select guardian_id from guardian_link_replica where student_id = ?", UUID.class, student);
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
