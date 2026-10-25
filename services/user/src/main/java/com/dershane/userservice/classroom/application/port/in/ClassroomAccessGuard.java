package com.dershane.userservice.classroom.application.port.in;

import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.shared.application.Actor;

/**
 * Who may see a classroom and its rosters. The rule lives here once; the enrollment module uses it
 * for the student and teacher lists of a classroom.
 */
public interface ClassroomAccessGuard {

  /** Branch admins, teachers of the classroom and its current students. */
  void requireCanView(Actor actor, Classroom classroom);

  /** Branch admins and the classroom's teachers only. */
  void requireStaffOf(Actor actor, Classroom classroom, String action);
}
