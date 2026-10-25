package com.dershane.userservice.classroom.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface GetClassroomQuery {

  /** Branch admins, the classroom's teachers and its current students. */
  ClassroomView get(Actor actor, UUID classroomId);
}
