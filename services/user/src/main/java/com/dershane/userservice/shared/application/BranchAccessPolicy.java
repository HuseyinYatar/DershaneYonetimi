package com.dershane.userservice.shared.application;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Branch scope rules: a super admin manages every branch, a branch admin only their own. Role
 * checks ({@code @PreAuthorize}) run first; this class decides on data the token alone can't.
 */
@Slf4j
@Component
public class BranchAccessPolicy {

  public void requireBranchAdmin(Actor caller, UUID branchId, String action) {
    if (caller.isSuperAdmin()) {
      log.atDebug()
          .addKeyValue("callerId", caller.id())
          .addKeyValue("branchId", branchId)
          .addKeyValue("action", action)
          .log("Super admin allowed on any branch");
      return;
    }
    // A missing branch claim must never match: null == null would open every branch.
    if (caller.isAdmin() && caller.branchId() != null && caller.branchId().equals(branchId)) {
      log.atDebug()
          .addKeyValue("callerId", caller.id())
          .addKeyValue("branchId", branchId)
          .addKeyValue("action", action)
          .log("Branch admin allowed on own branch");
      return;
    }
    throw Rejections.reject(
        log,
        ErrorCode.BRANCH_SCOPE_VIOLATION,
        "Caller is not an admin of the target branch",
        "callerId",
        caller.id(),
        "callerBranchId",
        caller.branchId(),
        "targetBranchId",
        branchId,
        "action",
        action);
  }

  /**
   * Resolves the branch filter for list endpoints: super admins may list any branch (or all when
   * {@code requested} is null); branch admins get their own branch and may not ask for another.
   */
  public UUID resolveBranchFilter(Actor caller, UUID requested, String action) {
    if (caller.isSuperAdmin()) {
      return requested;
    }
    if (requested == null) {
      log.atDebug()
          .addKeyValue("callerId", caller.id())
          .addKeyValue("action", action)
          .log("No branch filter given; defaulting to the caller's branch");
      requireBranchAdmin(caller, caller.branchId(), action);
      return caller.branchId();
    }
    requireBranchAdmin(caller, requested, action);
    return requested;
  }
}
