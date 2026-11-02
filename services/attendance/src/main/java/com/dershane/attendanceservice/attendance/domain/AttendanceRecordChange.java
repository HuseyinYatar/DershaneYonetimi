package com.dershane.attendanceservice.attendance.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * One line of the audit trail: a record's status before and after a change, who changed it and why.
 * Append-only. {@code oldStatus} is null when the change created the record (a student added later,
 * or an admin taking attendance after the day ended).
 */
@Getter
public class AttendanceRecordChange {

  /** Null until the change is saved. */
  private final UUID id;

  private final UUID recordId;
  private final AttendanceStatus oldStatus;
  private final AttendanceStatus newStatus;
  private final UUID changedBy;

  // Free text: never logged, masked in toString.
  private final String reason;

  private final Instant changedAt;

  public AttendanceRecordChange(
      UUID id,
      UUID recordId,
      AttendanceStatus oldStatus,
      AttendanceStatus newStatus,
      UUID changedBy,
      String reason,
      Instant changedAt) {
    this.id = id;
    this.recordId = recordId;
    this.oldStatus = oldStatus;
    this.newStatus = newStatus;
    this.changedBy = changedBy;
    this.reason = reason;
    this.changedAt = changedAt;
  }

  public static AttendanceRecordChange of(
      UUID recordId,
      AttendanceStatus oldStatus,
      AttendanceStatus newStatus,
      UUID changedBy,
      String reason,
      Instant changedAt) {
    return new AttendanceRecordChange(
        null, recordId, oldStatus, newStatus, changedBy, reason.strip(), changedAt);
  }

  @Override
  public String toString() {
    return "AttendanceRecordChange[recordId=" + recordId + "]";
  }
}
