package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.application.port.in.UserView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserQueryPort {

  Optional<UserView> findViewById(UUID userId);

  /** {@code branchId == null} lists the admins of every branch. */
  Page<UserView> findAdminViews(UUID branchId, Pageable pageable);
}
