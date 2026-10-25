package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface RemoveTeacherAssignmentUseCase {

  void remove(Actor actor, UUID teacherId, UUID assignmentId);
}
