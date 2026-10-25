package com.dershane.userservice.branch.application.service;

import com.dershane.userservice.branch.application.port.in.BranchView;
import com.dershane.userservice.branch.application.port.in.CreateBranchCommand;
import com.dershane.userservice.branch.application.port.in.CreateBranchUseCase;
import com.dershane.userservice.branch.application.port.in.DeactivateBranchUseCase;
import com.dershane.userservice.branch.application.port.in.GetBranchQuery;
import com.dershane.userservice.branch.application.port.in.ListBranchesQuery;
import com.dershane.userservice.branch.application.port.in.UpdateBranchCommand;
import com.dershane.userservice.branch.application.port.in.UpdateBranchUseCase;
import com.dershane.userservice.branch.application.port.out.BranchQueryPort;
import com.dershane.userservice.branch.application.port.out.BranchUsagePort;
import com.dershane.userservice.branch.application.port.out.LoadBranchPort;
import com.dershane.userservice.branch.application.port.out.SaveBranchPort;
import com.dershane.userservice.branch.domain.Branch;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import io.micrometer.observation.annotation.Observed;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class BranchService
    implements CreateBranchUseCase,
        UpdateBranchUseCase,
        DeactivateBranchUseCase,
        GetBranchQuery,
        ListBranchesQuery {

  private final LoadBranchPort loadBranch;
  private final SaveBranchPort saveBranch;
  private final BranchQueryPort branchQuery;
  private final BranchUsagePort branchUsage;
  private final BranchLookup branchLookup;
  private final BranchAccessPolicy accessPolicy;

  @Override
  @Transactional
  @Observed(name = "branch.create")
  public BranchView create(CreateBranchCommand command) {
    String code = command.code().toUpperCase(Locale.ROOT);
    if (loadBranch.existsByCode(code)) {
      throw Rejections.reject(
          log, ErrorCode.BRANCH_CODE_TAKEN, "Branch creation rejected: code in use", "code", code);
    }
    Branch branch = saveBranch.save(Branch.create(command.name().strip(), code));
    log.atInfo().addKeyValue("branchId", branch.getId()).log("Branch created");
    return requireView(branch.getId());
  }

  @Override
  @Transactional
  @Observed(name = "branch.update")
  public BranchView update(UUID branchId, UpdateBranchCommand command) {
    Branch branch = branchLookup.require(branchId);
    branch.rename(command.name().strip());
    saveBranch.save(branch);
    log.atInfo().addKeyValue("branchId", branchId).log("Branch renamed");
    return requireView(branchId);
  }

  @Override
  @Transactional
  @Observed(name = "branch.deactivate")
  public BranchView deactivate(UUID branchId) {
    Branch branch = branchLookup.require(branchId);
    if (!branch.deactivate(branchUsage.hasActiveClassrooms(branchId))) {
      log.atInfo().addKeyValue("branchId", branchId).log("Branch already inactive; no change");
      return requireView(branchId);
    }
    saveBranch.save(branch);
    log.atInfo().addKeyValue("branchId", branchId).log("Branch deactivated");
    return requireView(branchId);
  }

  @Override
  @Transactional(readOnly = true)
  public BranchView get(Actor actor, UUID branchId) {
    accessPolicy.requireBranchAdmin(actor, branchId, "branch.get");
    return requireView(branchId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<BranchView> list(Actor actor, Pageable pageable) {
    if (actor.isSuperAdmin()) {
      return branchQuery.findViews(pageable);
    }
    UUID ownBranch = accessPolicy.resolveBranchFilter(actor, null, "branch.list");
    return branchQuery.findViewsOf(ownBranch, pageable);
  }

  private BranchView requireView(UUID branchId) {
    return branchQuery
        .findViewById(branchId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.BRANCH_NOT_FOUND, "Branch not found", "branchId", branchId));
  }
}
