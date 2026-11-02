package com.dershane.attendanceservice.excuse.application.port.in;

import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListExcusesQuery {

  /**
   * Admins list the excuses of their branch; a guardian lists the ones they reported. Null filters
   * are not applied.
   */
  Page<ExcuseView> list(Actor actor, ExcuseStatus status, UUID studentId, Pageable pageable);
}
