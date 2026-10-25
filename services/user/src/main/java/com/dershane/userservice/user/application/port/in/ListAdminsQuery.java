package com.dershane.userservice.user.application.port.in;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListAdminsQuery {

  /** {@code branchId == null} lists the admins of every branch. */
  Page<UserView> list(UUID branchId, Pageable pageable);
}
