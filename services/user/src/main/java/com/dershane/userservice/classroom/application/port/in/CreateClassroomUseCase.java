package com.dershane.userservice.classroom.application.port.in;

import com.dershane.userservice.shared.application.Actor;

public interface CreateClassroomUseCase {

  /** Admins of the target branch (and super admins). */
  ClassroomView create(Actor actor, CreateClassroomCommand command);
}
