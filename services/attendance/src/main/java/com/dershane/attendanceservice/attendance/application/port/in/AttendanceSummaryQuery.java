package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.time.LocalDate;
import java.util.UUID;

public interface AttendanceSummaryQuery {

  /**
   * Counts per status for one student, within the same visibility as {@link ListRecordsQuery}. A
   * student defaults to themselves; everyone else must name the student.
   */
  AttendanceSummaryView summarize(Actor actor, UUID studentId, LocalDate from, LocalDate to);
}
