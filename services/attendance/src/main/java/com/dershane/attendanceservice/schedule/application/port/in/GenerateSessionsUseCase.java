package com.dershane.attendanceservice.schedule.application.port.in;

public interface GenerateSessionsUseCase {

  /**
   * Creates the sessions the active schedules call for, from today through {@code horizonDays} days
   * ahead. Safe to run again: days that already have a session are left alone.
   */
  GenerateSessionsResult generate(int horizonDays);
}
