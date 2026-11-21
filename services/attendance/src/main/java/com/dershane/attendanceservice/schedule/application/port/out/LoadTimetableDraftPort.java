package com.dershane.attendanceservice.schedule.application.port.out;

import com.dershane.attendanceservice.schedule.domain.TimetableDraft;
import java.util.Optional;
import java.util.UUID;

public interface LoadTimetableDraftPort {

  Optional<TimetableDraft> findByBranchId(UUID branchId);
}
