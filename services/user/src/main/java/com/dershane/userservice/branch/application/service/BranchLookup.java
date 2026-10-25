package com.dershane.userservice.branch.application.service;

import com.dershane.userservice.branch.application.port.in.LoadBranchQuery;
import com.dershane.userservice.branch.application.port.out.LoadBranchPort;
import com.dershane.userservice.branch.domain.Branch;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class BranchLookup implements LoadBranchQuery {

  private final LoadBranchPort branches;

  @Override
  @Transactional(readOnly = true)
  public Branch require(UUID branchId) {
    return branches
        .findById(branchId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.BRANCH_NOT_FOUND, "Branch not found", "branchId", branchId));
  }

  @Override
  @Transactional(readOnly = true)
  public Branch requireActive(UUID branchId, String action) {
    Branch branch = require(branchId);
    branch.requireActive(action);
    return branch;
  }
}
