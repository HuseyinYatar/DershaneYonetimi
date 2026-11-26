package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface DiscardTimetableDraftUseCase {

  /** Throws the branch's draft away; nothing happens when there is none. */
  void discard(Actor actor, UUID branchId);
}
