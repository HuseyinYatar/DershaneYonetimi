package com.dershane.attendanceservice.schedule.application.port.out;

import com.dershane.attendanceservice.schedule.application.port.in.ScheduleView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ScheduleQueryPort {

  Optional<ScheduleView> findViewById(UUID scheduleId);

  /** Null filters are not applied. */
  Page<ScheduleView> findViews(
      UUID branchId, UUID classroomId, UUID teacherId, Boolean active, Pageable pageable);
}
