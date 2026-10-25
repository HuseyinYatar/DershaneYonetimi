package com.dershane.userservice.branch.application.port.in;

import com.dershane.userservice.branch.domain.Branch;
import java.util.UUID;

/** For other modules: loads a branch and rejects (with a log line) when it cannot be used. */
public interface LoadBranchQuery {

  Branch require(UUID branchId);

  Branch requireActive(UUID branchId, String action);
}
