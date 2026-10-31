package com.dershane.attendanceservice.session.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface CancelSessionUseCase {

  /** Only a planned session can be cancelled; admins of the branch. */
  SessionView cancel(Actor actor, UUID sessionId);
}
