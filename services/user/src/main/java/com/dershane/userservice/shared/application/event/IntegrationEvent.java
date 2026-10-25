package com.dershane.userservice.shared.application.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Events this service publishes to other services (through the outbox). Each record is the {@code
 * data} part of the envelope and must match its JSON schema in {@code shared/events/user/}.
 *
 * <p>Payloads contain personal data for the consumers' local copies (e.g. contact details for the
 * notification service). They are never logged; their {@code toString} is masked.
 */
public sealed interface IntegrationEvent
    permits IntegrationEvent.StudentAddedToClassroom,
        IntegrationEvent.StudentRemovedFromClassroom,
        IntegrationEvent.UserUpdated,
        IntegrationEvent.GuardianLinked,
        IntegrationEvent.GuardianUnlinked {

  @JsonIgnore
  EventType type();

  /** The entity the event is about; used for tracing and ordering diagnostics. */
  @JsonIgnore
  UUID aggregateId();

  record StudentAddedToClassroom(
      UUID membershipId,
      UUID studentId,
      UUID classroomId,
      UUID branchId,
      String studentNumber,
      String fullName,
      LocalDate startDate)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.STUDENT_ADDED_TO_CLASSROOM;
    }

    @Override
    public UUID aggregateId() {
      return studentId;
    }

    @Override
    public String toString() {
      return "StudentAddedToClassroom[membershipId=" + membershipId + "]";
    }
  }

  record StudentRemovedFromClassroom(
      UUID membershipId, UUID studentId, UUID classroomId, UUID branchId, LocalDate endDate)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.STUDENT_REMOVED_FROM_CLASSROOM;
    }

    @Override
    public UUID aggregateId() {
      return studentId;
    }
  }

  /**
   * Current state of a user, sent on creation and on every change (upsert). Consumers drop an event
   * whose {@code recordVersion} is lower than the one they already stored.
   */
  record UserUpdated(
      UUID userId,
      String role,
      UUID branchId,
      String firstName,
      String lastName,
      String email,
      String phone,
      boolean active,
      String studentNumber,
      long recordVersion)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.USER_UPDATED;
    }

    @Override
    public UUID aggregateId() {
      return userId;
    }

    @Override
    public String toString() {
      return "UserUpdated[userId=" + userId + ", recordVersion=" + recordVersion + "]";
    }
  }

  /** Sent on a new link and when the relationship of an existing link changes. */
  record GuardianLinked(UUID guardianId, UUID studentId, String relationship)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.GUARDIAN_LINKED;
    }

    @Override
    public UUID aggregateId() {
      return studentId;
    }
  }

  record GuardianUnlinked(UUID guardianId, UUID studentId) implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.GUARDIAN_UNLINKED;
    }

    @Override
    public UUID aggregateId() {
      return studentId;
    }
  }
}
