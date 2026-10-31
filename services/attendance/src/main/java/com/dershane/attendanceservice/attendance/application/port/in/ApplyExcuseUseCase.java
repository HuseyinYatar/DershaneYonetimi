package com.dershane.attendanceservice.attendance.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

public interface ApplyExcuseUseCase {

  /**
   * Turns the student's {@code ABSENT} records in completed sessions between the dates into {@code
   * EXCUSED} linked to the excuse, each with an audit line. No integration event: the guardian
   * reported the excuse themselves. Called inside the approval transaction.
   *
   * @return how many records changed
   */
  int apply(UUID excuseId, UUID studentId, LocalDate fromDate, LocalDate toDate, UUID decidedBy);
}
