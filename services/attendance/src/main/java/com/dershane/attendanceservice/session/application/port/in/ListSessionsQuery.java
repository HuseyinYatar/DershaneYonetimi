package com.dershane.attendanceservice.session.application.port.in;

import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.application.Actor;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListSessionsQuery {

  /**
   * Admins list their branch (the branch filter is for super admins); a teacher lists only their
   * own sessions. Null filters are not applied.
   */
  Page<SessionView> list(
      Actor actor,
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      LocalDate from,
      LocalDate to,
      SessionStatus status,
      Pageable pageable);
}
