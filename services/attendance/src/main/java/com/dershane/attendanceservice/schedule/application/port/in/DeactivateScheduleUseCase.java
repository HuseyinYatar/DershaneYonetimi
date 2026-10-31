package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface DeactivateScheduleUseCase {

  /** Deleting a schedule means deactivating it; its sessions stay. Repeating it changes nothing. */
  ScheduleView deactivate(Actor actor, UUID scheduleId);
}
