package com.dershane.attendanceservice.schedule.application.port.out;

import com.dershane.attendanceservice.schedule.domain.TimetableSetting;

public interface SaveTimetableSettingPort {

  /** Inserts a new setting or updates a loaded one; returns it with the current version. */
  TimetableSetting save(TimetableSetting setting);
}
