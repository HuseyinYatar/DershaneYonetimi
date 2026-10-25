package com.dershane.userservice.branch.application.port.in;

import java.util.UUID;

public interface DeactivateBranchUseCase {

  /** Fails while the branch still has active classrooms. */
  BranchView deactivate(UUID branchId);
}
