package com.dershane.userservice.branch.application.port.in;

import java.util.UUID;

public interface UpdateBranchUseCase {

  BranchView update(UUID branchId, UpdateBranchCommand command);
}
