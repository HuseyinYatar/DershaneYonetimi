package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface ChangeAssignmentHoursUseCase {

  /** Sets the weekly lesson hours the classroom needs for the assignment (admins of the branch). */
  TeacherAssignmentView changeWeeklyHours(
      Actor actor, UUID teacherId, UUID assignmentId, int weeklyHours);
}
