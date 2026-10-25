package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.List;
import java.util.UUID;

public interface ListClassroomStudentsQuery {

  /** Current students of a classroom; visible to branch admins and the classroom's teachers. */
  List<ClassroomStudentView> students(Actor actor, UUID classroomId);
}
