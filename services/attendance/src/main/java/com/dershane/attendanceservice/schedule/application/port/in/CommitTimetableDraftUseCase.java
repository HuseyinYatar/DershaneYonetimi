package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.time.LocalDate;
import java.util.UUID;

public interface CommitTimetableDraftUseCase {

  /**
   * Opens a class schedule for every draft lesson, valid from {@code validFrom}, and drops the
   * draft. All or nothing.
   *
   * @return how many schedules were created
   */
  int commit(Actor actor, UUID branchId, LocalDate validFrom);
}
