package com.dershane.taskservice.shared.application.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

/**
 * Events this service publishes to other services (through the outbox). Each record is the {@code
 * data} part of the envelope and must match its JSON schema in {@code shared/events/task/}.
 *
 * <p>Payloads carry identifiers, the task title and the due time only: no names, no submission text
 * and no feedback (KVKK). Consumers resolve what they need from their own copies or from the REST
 * API.
 */
public sealed interface IntegrationEvent
    permits IntegrationEvent.TaskAssigned,
        IntegrationEvent.TaskDueSoon,
        IntegrationEvent.TaskOverdue,
        IntegrationEvent.SubmissionGraded {

  @JsonIgnore
  EventType type();

  /** The entity the event is about; used for tracing and ordering diagnostics. */
  @JsonIgnore
  UUID aggregateId();

  /** An assignment was handed to a student. */
  record TaskAssigned(
      UUID assignmentId,
      UUID taskId,
      UUID studentId,
      UUID branchId,
      String title,
      Instant dueAt,
      String recurrence)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.TASK_ASSIGNED;
    }

    @Override
    public UUID aggregateId() {
      return assignmentId;
    }
  }

  /** An open assignment entered its reminder window. */
  record TaskDueSoon(
      UUID assignmentId, UUID taskId, UUID studentId, UUID branchId, String title, Instant dueAt)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.TASK_DUE_SOON;
    }

    @Override
    public UUID aggregateId() {
      return assignmentId;
    }
  }

  /** The due time passed without a submission. */
  record TaskOverdue(
      UUID assignmentId, UUID taskId, UUID studentId, UUID branchId, String title, Instant dueAt)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.TASK_OVERDUE;
    }

    @Override
    public UUID aggregateId() {
      return assignmentId;
    }
  }

  /** A submission was evaluated (or evaluated again). {@code score} is null for feedback only. */
  record SubmissionGraded(
      UUID assignmentId,
      UUID taskId,
      UUID studentId,
      UUID branchId,
      String title,
      Instant dueAt,
      Integer score)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.SUBMISSION_GRADED;
    }

    @Override
    public UUID aggregateId() {
      return assignmentId;
    }
  }
}
