package com.dershane.attendanceservice.session.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface ReassignTeacherUseCase {

  /** A substitute takes a planned session; admins of the branch. */
  SessionView reassign(Actor actor, UUID sessionId, UUID teacherId);
}
