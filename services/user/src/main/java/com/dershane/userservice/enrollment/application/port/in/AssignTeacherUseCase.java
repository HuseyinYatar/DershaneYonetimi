package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface AssignTeacherUseCase {

  /** Assigns a teacher to teach a subject in a classroom (admins of the teacher's branch). */
  TeacherAssignmentView assign(Actor actor, UUID teacherId, AssignTeacherCommand command);
}
