package com.dershane.attendanceservice.excuse.application.port.out;

import com.dershane.attendanceservice.excuse.application.port.in.ExcuseView;
import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExcuseQueryPort {

  Optional<ExcuseView> findViewById(UUID excuseId);

  /** Null filters are not applied. */
  Page<ExcuseView> findViews(
      UUID branchId, UUID requestedBy, UUID studentId, ExcuseStatus status, Pageable pageable);
}
