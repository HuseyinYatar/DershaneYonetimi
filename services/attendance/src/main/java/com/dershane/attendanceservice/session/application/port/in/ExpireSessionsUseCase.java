package com.dershane.attendanceservice.session.application.port.in;

public interface ExpireSessionsUseCase {

  /**
   * Marks planned sessions of past days as expired (no attendance, no records, no absences).
   *
   * @return how many sessions were expired
   */
  int expireElapsed();
}
