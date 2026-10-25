package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface UpdateStudentUseCase {

  StudentView update(Actor actor, UUID studentId, UpdateStudentCommand command);
}
