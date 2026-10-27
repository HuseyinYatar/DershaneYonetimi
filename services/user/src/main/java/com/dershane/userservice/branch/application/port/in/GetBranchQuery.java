package com.dershane.userservice.branch.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface GetBranchQuery {

  /** Admins of that branch and super admins. */
  BranchView get(Actor actor, UUID branchId);
}
