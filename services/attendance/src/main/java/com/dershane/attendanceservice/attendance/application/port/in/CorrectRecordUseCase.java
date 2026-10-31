package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;

public interface CorrectRecordUseCase {

  /**
   * Changes one student's status in a completed session, or adds a record for a student who joined
   * the classroom later. A reason is required and every change is audited. The session's teacher
   * may correct within the correction window; admins of its branch at any time.
   */
  AttendanceRecordView correct(Actor actor, CorrectRecordCommand command);
}
