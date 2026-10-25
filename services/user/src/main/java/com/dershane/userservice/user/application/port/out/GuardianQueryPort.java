package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.application.port.in.GuardianView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GuardianQueryPort {

  Optional<GuardianView> findGuardianViewById(UUID guardianId);

  /** Null filters are not applied. */
  Page<GuardianView> findGuardianViews(UUID branchId, Boolean active, Pageable pageable);
}
