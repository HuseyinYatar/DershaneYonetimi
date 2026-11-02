package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface SessionRosterQuery {

  /** The students of a session with their current status; the session's teacher and its admins. */
  SessionRosterView rosterOf(Actor actor, UUID sessionId);
}
