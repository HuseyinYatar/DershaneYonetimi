package com.dershane.attendanceservice.attendance.domain;

/** Which integration event, if any, a record's current state calls for. */
public enum NotificationKind {
  NONE,
  /** Unexcused absence: {@code StudentAbsent}. */
  ABSENCE,
  /** Excused by the teacher without an approved excuse: {@code StudentExcusedByTeacher}. */
  TEACHER_EXCUSE
}
