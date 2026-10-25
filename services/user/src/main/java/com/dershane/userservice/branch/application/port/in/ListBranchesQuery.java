package com.dershane.userservice.branch.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListBranchesQuery {

  /** Super admins see every branch; branch admins see only their own. */
  Page<BranchView> list(Actor actor, Pageable pageable);
}
