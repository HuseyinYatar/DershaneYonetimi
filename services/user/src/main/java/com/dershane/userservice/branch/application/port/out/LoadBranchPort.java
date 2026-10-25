package com.dershane.userservice.branch.application.port.out;

import com.dershane.userservice.branch.domain.Branch;
import java.util.Optional;
import java.util.UUID;

public interface LoadBranchPort {

  Optional<Branch> findById(UUID branchId);

  boolean existsByCode(String code);
}
