package com.dershane.attendanceservice.session.application.port.out;

import com.dershane.attendanceservice.session.application.port.in.SessionView;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SessionQueryPort {

  Optional<SessionView> findViewById(UUID sessionId);

  /** Null filters are not applied. */
  Page<SessionView> findViews(
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      LocalDate from,
      LocalDate to,
      SessionStatus status,
      Pageable pageable);
}
