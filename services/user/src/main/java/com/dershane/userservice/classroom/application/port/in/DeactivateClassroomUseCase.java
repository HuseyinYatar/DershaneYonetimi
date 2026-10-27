package com.dershane.userservice.classroom.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface DeactivateClassroomUseCase {

  /** Fails while students are enrolled. */
  ClassroomView deactivate(Actor actor, UUID classroomId);
}
