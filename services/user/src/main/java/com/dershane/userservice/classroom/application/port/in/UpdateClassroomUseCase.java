package com.dershane.userservice.classroom.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface UpdateClassroomUseCase {

  ClassroomView update(Actor actor, UUID classroomId, UpdateClassroomCommand command);
}
