package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface GetTimetableDraftQuery {

  /** The branch's current draft (the caller's branch when {@code branchId} is null). */
  TimetableDraftView current(Actor actor, UUID branchId);
}
