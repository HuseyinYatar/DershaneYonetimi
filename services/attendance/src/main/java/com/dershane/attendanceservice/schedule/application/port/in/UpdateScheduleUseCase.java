package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface UpdateScheduleUseCase {

  /** Affects the sessions generated from now on, not those already created. */
  ScheduleView update(Actor actor, UUID scheduleId, UpdateScheduleCommand command);
}
