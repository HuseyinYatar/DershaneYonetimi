package com.dershane.attendanceservice.schedule.application.port.out;

import com.dershane.attendanceservice.schedule.domain.ClassSchedule;

public interface SaveSchedulePort {

  /** Inserts a new schedule or updates a loaded one; returns it with id and current version. */
  ClassSchedule save(ClassSchedule schedule);
}
