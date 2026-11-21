package com.dershane.attendanceservice.schedule.application.port.out;

import com.dershane.attendanceservice.schedule.domain.TimetableDraft;
import java.util.UUID;

public interface SaveTimetableDraftPort {

  /** Inserts a new draft, or rewrites the lessons of a saved one; returns it as stored. */
  TimetableDraft save(TimetableDraft draft);

  void deleteByBranchId(UUID branchId);
}
