package com.dershane.attendanceservice.schedule.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListSchedulesQuery {

  /** The branch defaults to the caller's branch; null filters are not applied. */
  Page<ScheduleView> list(
      Actor actor,
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      Boolean active,
      Pageable pageable);
}
