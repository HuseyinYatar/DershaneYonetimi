package com.dershane.attendanceservice.session.domain;

import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * One lesson of one classroom on one day (diagram: DersOturumu). It comes from a weekly schedule or
 * is opened by an admin (make-up lesson, study hour). The branch, classroom and subject never
 * change; only the teacher (a substitute) and the status do.
 */
@Slf4j
@Getter
public class ClassSession {

  /** Null until the session is saved for the first time. */
  private final UUID id;

  /** Version the session was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  /** Null for an ad-hoc session. */
  private final UUID scheduleId;

  private final UUID branchId;
  private final UUID classroomId;
  private UUID teacherId;
  private final UUID subjectId;
  private final LocalDate sessionDate;
  private final LocalTime startsAt;
  private final LocalTime endsAt;
  private SessionStatus status;
  private Instant takenAt;
  private UUID takenBy;

  /** Rehydrates a saved session. Use {@link #plan} for a new one. */
  // A rehydrating constructor takes every field of the aggregate.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public ClassSession(
      UUID id,
      Long version,
      UUID scheduleId,
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      UUID subjectId,
      LocalDate sessionDate,
      LocalTime startsAt,
      LocalTime endsAt,
      SessionStatus status,
      Instant takenAt,
      UUID takenBy) {
    this.id = id;
    this.version = version;
    this.scheduleId = scheduleId;
    this.branchId = branchId;
    this.classroomId = classroomId;
    this.teacherId = teacherId;
    this.subjectId = subjectId;
    this.sessionDate = sessionDate;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.status = status;
    this.takenAt = takenAt;
    this.takenBy = takenBy;
  }

  public static ClassSession plan(
      UUID scheduleId,
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      UUID subjectId,
      LocalDate sessionDate,
      LocalTime startsAt,
      LocalTime endsAt) {
    if (!endsAt.isAfter(startsAt)) {
      throw Rejections.reject(
          log,
          ErrorCode.INVALID_TIME_RANGE,
          "Session end time is not after its start time",
          "classroomId",
          classroomId);
    }
    return new ClassSession(
        null,
        null,
        scheduleId,
        branchId,
        classroomId,
        teacherId,
        subjectId,
        sessionDate,
        startsAt,
        endsAt,
        SessionStatus.PLANNED,
        null,
        null);
  }

  public boolean isTeacher(UUID userId) {
    return teacherId.equals(userId);
  }

  /** A cancelled session never takes attendance; a planned one is called off. */
  public void cancel() {
    requirePlanned("cancel");
    this.status = SessionStatus.CANCELLED;
  }

  /** Substitute teacher; only before attendance was taken. */
  public void reassignTeacher(UUID newTeacherId) {
    requirePlanned("reassign the teacher of");
    this.teacherId = newTeacherId;
  }

  /**
   * Marks attendance as taken. Allowed from {@code PLANNED}, and from {@code EXPIRED} when an admin
   * takes it late.
   */
  public void complete(UUID takenByUser, Instant takenAtInstant) {
    requireOpen();
    this.status = SessionStatus.COMPLETED;
    this.takenBy = takenByUser;
    this.takenAt = takenAtInstant;
  }

  /**
   * Expires a planned session whose day is over.
   *
   * @return false when nothing changed (not planned, or its day is not over yet)
   */
  public boolean expire(LocalDate today) {
    if (status != SessionStatus.PLANNED || !sessionDate.isBefore(today)) {
      log.atDebug()
          .addKeyValue("sessionId", id)
          .addKeyValue("status", status)
          .log("Session not expired: not planned or its day is not over");
      return false;
    }
    this.status = SessionStatus.EXPIRED;
    return true;
  }

  /**
   * Decides whether attendance may be taken now and how.
   *
   * <p>Inside the window (from {@code early} before the lesson to the end of its day) the session
   * teacher or an admin may take it. After the day ended, or once the session is {@code EXPIRED},
   * only an admin may, and only with a reason.
   *
   * @param admin the caller acts as admin of this session's branch
   * @param reasonGiven the request carries a non-blank reason
   */
  public TakeMode checkTakeable(
      LocalDateTime now, Duration early, boolean admin, boolean reasonGiven) {
    requireOpen();
    boolean dayOver = status == SessionStatus.EXPIRED || sessionDate.isBefore(now.toLocalDate());
    if (dayOver) {
      if (!admin) {
        throw Rejections.reject(
            log,
            ErrorCode.SESSION_EXPIRED,
            "Attendance rejected: the session day is over and the caller is not an admin",
            "sessionId",
            id);
      }
      if (!reasonGiven) {
        throw Rejections.reject(
            log,
            ErrorCode.REASON_REQUIRED,
            "Late attendance rejected: an admin must give a reason",
            "sessionId",
            id);
      }
      return TakeMode.LATE_BY_ADMIN;
    }
    if (now.isBefore(LocalDateTime.of(sessionDate, startsAt).minus(early))) {
      throw Rejections.reject(
          log,
          ErrorCode.ATTENDANCE_TOO_EARLY,
          "Attendance rejected: too long before the session starts",
          "sessionId",
          id);
    }
    return TakeMode.NORMAL;
  }

  /** Corrections need a finished attendance record to correct. */
  public void requireCompleted(String action) {
    if (status != SessionStatus.COMPLETED) {
      throw Rejections.reject(
          log,
          ErrorCode.SESSION_NOT_COMPLETED,
          "Operation rejected: attendance was not taken for this session",
          "sessionId",
          id,
          "status",
          status,
          "action",
          action);
    }
  }

  private void requirePlanned(String action) {
    if (status != SessionStatus.PLANNED) {
      throw Rejections.reject(
          log,
          ErrorCode.SESSION_NOT_PLANNED,
          "Operation rejected: only a planned session can be changed",
          "sessionId",
          id,
          "status",
          status,
          "action",
          action);
    }
  }

  private void requireOpen() {
    if (status == SessionStatus.COMPLETED) {
      throw Rejections.reject(
          log,
          ErrorCode.ATTENDANCE_ALREADY_TAKEN,
          "Attendance already taken for this session",
          "sessionId",
          id);
    }
    if (status == SessionStatus.CANCELLED) {
      throw Rejections.reject(
          log,
          ErrorCode.SESSION_CANCELLED,
          "Operation rejected: session was cancelled",
          "sessionId",
          id);
    }
  }
}
