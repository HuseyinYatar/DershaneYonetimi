package com.dershane.attendanceservice.schedule.application.port.out;

import com.dershane.attendanceservice.schedule.domain.TimetableSetting;
import java.util.Optional;
import java.util.UUID;

public interface LoadTimetableSettingPort {

  Optional<TimetableSetting> findByBranchId(UUID branchId);
}
