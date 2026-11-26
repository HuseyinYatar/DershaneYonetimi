package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface GetTimetableSettingQuery {

  /**
   * The branch's grid, or the defaults when it has none; {@code branchId} null = caller's branch.
   */
  TimetableSettingView get(Actor actor, UUID branchId);
}
