package com.dershane.attendanceservice.session.domain;

/**
 * Where a class session is in its life. Only {@code PLANNED} and {@code EXPIRED} take attendance.
 */
public enum SessionStatus {
  /** Created, attendance not taken yet. */
  PLANNED,
  /** Attendance was taken. */
  COMPLETED,
  /** The lesson did not take place; no records, no absences. */
  CANCELLED,
  /** The session day ended without attendance; only an admin can still take it. */
  EXPIRED
}
