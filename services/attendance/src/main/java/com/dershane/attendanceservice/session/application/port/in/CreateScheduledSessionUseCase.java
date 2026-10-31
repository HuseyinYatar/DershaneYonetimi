package com.dershane.attendanceservice.session.application.port.in;

public interface CreateScheduledSessionUseCase {

  /**
   * Creates the session a weekly schedule calls for. Idempotent per schedule and day; a conflict
   * with another session is logged and skipped so one bad schedule does not stop the whole run.
   */
  ScheduledSessionOutcome createFromSchedule(ScheduledSessionCommand command);
}
