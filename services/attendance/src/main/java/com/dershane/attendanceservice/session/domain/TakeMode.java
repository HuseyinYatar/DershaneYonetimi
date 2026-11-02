package com.dershane.attendanceservice.session.domain;

/** How attendance is being taken for a session. */
public enum TakeMode {
  /** Inside the normal window (from shortly before the lesson to the end of its day). */
  NORMAL,
  /** After the session day ended: an admin with a reason. */
  LATE_BY_ADMIN
}
