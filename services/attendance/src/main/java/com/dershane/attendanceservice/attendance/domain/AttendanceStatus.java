package com.dershane.attendanceservice.attendance.domain;

public enum AttendanceStatus {
  PRESENT,
  /** Missing without an approved excuse; the guardian is notified. */
  ABSENT,
  LATE,
  /** Missing with permission: an approved excuse, or the teacher's own decision. */
  EXCUSED
}
