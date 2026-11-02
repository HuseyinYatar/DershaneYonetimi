package com.dershane.attendanceservice.session.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface GetSessionQuery {

  /** The session's teacher and admins of its branch. */
  SessionView get(Actor actor, UUID sessionId);
}
