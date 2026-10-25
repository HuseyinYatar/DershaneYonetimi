package com.dershane.userservice.branch.application.port.out;

import com.dershane.userservice.branch.domain.Branch;

public interface SaveBranchPort {

  /** Inserts a new branch or updates a loaded one; returns it with id and current version. */
  Branch save(Branch branch);
}
