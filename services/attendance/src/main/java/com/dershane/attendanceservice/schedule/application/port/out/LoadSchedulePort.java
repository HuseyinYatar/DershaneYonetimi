package com.dershane.attendanceservice.schedule.application.port.out;

import com.dershane.attendanceservice.schedule.domain.ClassSchedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadSchedulePort {

  Optional<ClassSchedule> findById(UUID scheduleId);

  /** Active schedules whose validity range touches {@code from..to}. */
  List<ClassSchedule> findActiveValidBetween(LocalDate from, LocalDate to);
}
