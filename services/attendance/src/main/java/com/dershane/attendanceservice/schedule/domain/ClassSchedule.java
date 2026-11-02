package com.dershane.attendanceservice.schedule.domain;

import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A lesson that repeats every week on one day (diagram: DersProgrami), valid for a date range. A
 * daily job turns it into sessions for the coming days. Branch and classroom never change; changing
 * the teacher, subject or time affects the sessions generated from then on, not those already
 * created.
 */
@Slf4j
@Getter
public class ClassSchedule {

  /** Null until the schedule is saved for the first time. */
  private final UUID id;

  /** Version the schedule was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID branchId;
  private final UUID classroomId;
  private UUID teacherId;
  private UUID subjectId;
  private DayOfWeek dayOfWeek;
  private LocalTime startsAt;
  private LocalTime endsAt;
  private LocalDate validFrom;
  private LocalDate validTo;
  private boolean active;

  /** Rehydrates a saved schedule. Use {@link #create} for a new one. */
  // A rehydrating constructor takes every field of the aggregate.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public ClassSchedule(
      UUID id,
      Long version,
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      UUID subjectId,
      DayOfWeek dayOfWeek,
      LocalTime startsAt,
      LocalTime endsAt,
      LocalDate validFrom,
      LocalDate validTo,
      boolean active) {
    this.id = id;
    this.version = version;
    this.branchId = branchId;
    this.classroomId = classroomId;
    this.teacherId = teacherId;
    this.subjectId = subjectId;
    this.dayOfWeek = dayOfWeek;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.validFrom = validFrom;
    this.validTo = validTo;
    this.active = active;
  }

  public static ClassSchedule create(
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      UUID subjectId,
      DayOfWeek dayOfWeek,
      LocalTime startsAt,
      LocalTime endsAt,
      LocalDate validFrom,
      LocalDate validTo) {
    requireValidRanges(classroomId, startsAt, endsAt, validFrom, validTo);
    return new ClassSchedule(
        null,
        null,
        branchId,
        classroomId,
        teacherId,
        subjectId,
        dayOfWeek,
        startsAt,
        endsAt,
        validFrom,
        validTo,
        true);
  }

  /** Replaces everything that may change. */
  public void revise(
      UUID newTeacherId,
      UUID newSubjectId,
      DayOfWeek newDayOfWeek,
      LocalTime newStartsAt,
      LocalTime newEndsAt,
      LocalDate newValidFrom,
      LocalDate newValidTo) {
    requireValidRanges(classroomId, newStartsAt, newEndsAt, newValidFrom, newValidTo);
    this.teacherId = newTeacherId;
    this.subjectId = newSubjectId;
    this.dayOfWeek = newDayOfWeek;
    this.startsAt = newStartsAt;
    this.endsAt = newEndsAt;
    this.validFrom = newValidFrom;
    this.validTo = newValidTo;
  }

  /**
   * Stops the schedule from producing new sessions.
   *
   * @return false when it was already inactive (nothing changed)
   */
  public boolean deactivate() {
    if (!active) {
      return false;
    }
    this.active = false;
    return true;
  }

  /** Whether the schedule calls for a session on {@code date}. */
  public boolean appliesOn(LocalDate date) {
    return active
        && date.getDayOfWeek() == dayOfWeek
        && !date.isBefore(validFrom)
        && (validTo == null || !date.isAfter(validTo));
  }

  private static void requireValidRanges(
      UUID classroomId,
      LocalTime startsAt,
      LocalTime endsAt,
      LocalDate validFrom,
      LocalDate validTo) {
    if (!endsAt.isAfter(startsAt)) {
      throw Rejections.reject(
          log,
          ErrorCode.INVALID_TIME_RANGE,
          "Schedule end time is not after its start time",
          "classroomId",
          classroomId);
    }
    if (validTo != null && validTo.isBefore(validFrom)) {
      throw Rejections.reject(
          log,
          ErrorCode.INVALID_DATE_RANGE,
          "Schedule valid-to date is before its valid-from date",
          "classroomId",
          classroomId);
    }
  }
}
