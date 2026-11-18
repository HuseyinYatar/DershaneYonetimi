package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;

public interface SaveTimetableSettingUseCase {

  /** Creates or replaces the branch's grid (admins of that branch, super admins). */
  TimetableSettingView save(Actor actor, SaveTimetableSettingCommand command);
}
