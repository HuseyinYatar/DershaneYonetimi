package com.dershane.attendanceservice.attendance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * One student's attendance in one session (diagram: YoklamaKaydi). Records are never deleted. An
 * {@code EXCUSED} record has an {@code excuseId} when an approved guardian excuse covers it, and
 * none when the teacher excused the student on their own.
 */
@Getter
public class AttendanceRecord {

  /** Null until the record is saved for the first time. */
  private final UUID id;

  /** Version the record was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID sessionId;
  private final UUID studentId;
  private AttendanceStatus status;
  private Instant markedAt;
  private UUID markedBy;
  private UUID excuseId;

  /** Optional remark of the teacher; free text, never logged. */
  private final String note;

  /** Rehydrates a saved record. Use {@link #mark} for a new one. */
  public AttendanceRecord(
      UUID id,
      Long version,
      UUID sessionId,
      UUID studentId,
      AttendanceStatus status,
      Instant markedAt,
      UUID markedBy,
      UUID excuseId,
      String note) {
    this.id = id;
    this.version = version;
    this.sessionId = sessionId;
    this.studentId = studentId;
    this.status = status;
    this.markedAt = markedAt;
    this.markedBy = markedBy;
    this.excuseId = excuseId;
    this.note = note;
  }

  public static AttendanceRecord mark(
      UUID sessionId,
      UUID studentId,
      AttendanceStatus status,
      UUID excuseId,
      String note,
      Instant markedAt,
      UUID markedBy) {
    return new AttendanceRecord(
        null, null, sessionId, studentId, status, markedAt, markedBy, excuseId, blankToNull(note));
  }

  /**
   * Changes the outcome.
   *
   * @return false when status and excuse stay the same (nothing changed)
   */
  public boolean changeTo(
      AttendanceStatus newStatus, UUID newExcuseId, Instant changedAt, UUID changedBy) {
    if (status == newStatus && Objects.equals(excuseId, newExcuseId)) {
      return false;
    }
    this.status = newStatus;
    this.excuseId = newExcuseId;
    this.markedAt = changedAt;
    this.markedBy = changedBy;
    return true;
  }

  /** The event this record's current state calls for. */
  public NotificationKind notificationKind() {
    if (status == AttendanceStatus.ABSENT) {
      return NotificationKind.ABSENCE;
    }
    if (status == AttendanceStatus.EXCUSED && excuseId == null) {
      return NotificationKind.TEACHER_EXCUSE;
    }
    return NotificationKind.NONE;
  }

  @Override
  public String toString() {
    return "AttendanceRecord[id=" + id + "]";
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }
}
