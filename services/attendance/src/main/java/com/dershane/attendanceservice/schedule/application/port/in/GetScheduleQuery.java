package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface GetScheduleQuery {

  /** Admins of the schedule's branch. */
  ScheduleView get(Actor actor, UUID scheduleId);
}
