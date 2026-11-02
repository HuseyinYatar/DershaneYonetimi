package com.dershane.attendanceservice.session.application.port.in;

public enum ScheduledSessionOutcome {
  CREATED,
  /** The schedule already has a session on that day (the job ran before). */
  ALREADY_EXISTS,
  /** The classroom or the teacher already has a session at that time. */
  SKIPPED_CONFLICT
}
