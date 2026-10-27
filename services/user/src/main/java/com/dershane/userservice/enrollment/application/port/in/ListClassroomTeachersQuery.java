package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.List;
import java.util.UUID;

public interface ListClassroomTeachersQuery {

  /** Teachers and subjects of a classroom; visible to whoever may view the classroom. */
  List<ClassroomTeacherView> teachers(Actor actor, UUID classroomId);
}
