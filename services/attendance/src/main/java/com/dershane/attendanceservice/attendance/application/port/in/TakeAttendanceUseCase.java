package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;

public interface TakeAttendanceUseCase {

  /**
   * Takes attendance once for a session. The list must hold every student of the roster exactly
   * once. The session's teacher or an admin of its branch may submit from shortly before the lesson
   * to the end of its day; afterwards only an admin, with a reason.
   */
  AttendanceSubmissionView take(Actor actor, TakeAttendanceCommand command);
}
