package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.List;
import java.util.UUID;

public interface ListTeacherAssignmentsQuery {

  /** Branch admins, or the teacher themself. */
  List<TeacherAssignmentView> list(Actor actor, UUID teacherId);
}
