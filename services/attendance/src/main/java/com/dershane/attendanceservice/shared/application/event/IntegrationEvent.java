package com.dershane.attendanceservice.shared.application.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Events this service publishes to other services (through the outbox). Each record is the {@code
 * data} part of the envelope and must match its JSON schema in {@code shared/events/attendance/}.
 *
 * <p>Payloads carry identifiers only: no names and no free-text reasons (KVKK). Consumers resolve
 * what they need from their own copies or from the REST API.
 */
public sealed interface IntegrationEvent
    permits IntegrationEvent.StudentAbsent, IntegrationEvent.StudentExcusedByTeacher {

  @JsonIgnore
  EventType type();

  /** The entity the event is about; used for tracing and ordering diagnostics. */
  @JsonIgnore
  UUID aggregateId();

  /** An unexcused absence: the record ended as {@code ABSENT}. */
  record StudentAbsent(
      UUID recordId,
      UUID sessionId,
      UUID studentId,
      UUID classroomId,
      UUID branchId,
      UUID subjectId,
      LocalDate sessionDate,
      LocalTime startTime,
      LocalTime endTime)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.STUDENT_ABSENT;
    }

    @Override
    public UUID aggregateId() {
      return recordId;
    }
  }

  /** The teacher excused the student without an approved guardian excuse covering the date. */
  record StudentExcusedByTeacher(
      UUID recordId,
      UUID sessionId,
      UUID studentId,
      UUID classroomId,
      UUID branchId,
      UUID subjectId,
      LocalDate sessionDate,
      LocalTime startTime,
      LocalTime endTime)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.STUDENT_EXCUSED_BY_TEACHER;
    }

    @Override
    public UUID aggregateId() {
      return recordId;
    }
  }
}
