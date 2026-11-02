package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;

public interface CreateScheduleUseCase {

  /** Admins of the target branch (and super admins). */
  ScheduleView create(Actor actor, CreateScheduleCommand command);
}
