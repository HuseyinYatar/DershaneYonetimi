package com.dershane.userservice.user.application.service;

import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.out.LoadStudentGuardianPort;
import com.dershane.userservice.user.application.port.out.StudentRelationsPort;
import com.dershane.userservice.user.domain.Role;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Who may see or change a user profile. These checks need data (class lists, guardian links), so
 * they can't be done by the gateway or by role checks alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UserAccessPolicy {

  private final BranchAccessPolicy branchAccess;
  private final StudentRelationsPort studentRelations;
  private final LoadStudentGuardianPort studentGuardians;

  /**
   * Allowed: the user themself, a super admin, an admin of the user's branch, a teacher of the
   * student's current classroom, the student's guardian.
   */
  void requireCanView(Actor caller, User target) {
    String reason = viewReason(caller, target);
    if (reason == null) {
      throw Rejections.reject(
          log,
          ErrorCode.ACCESS_DENIED,
          "Caller may not view this user",
          "callerId",
          caller.id(),
          "targetId",
          target.getId(),
          "targetRole",
          target.getRole());
    }
    log.atDebug()
        .addKeyValue("callerId", caller.id())
        .addKeyValue("targetId", target.getId())
        .addKeyValue("reason", reason)
        .log("User view allowed");
  }

  /** Admins manage users of their own branch; only super admins manage other admins. */
  void requireCanManage(Actor caller, User target, String action) {
    if (target.getRole() == Role.ADMIN && !caller.isSuperAdmin()) {
      throw Rejections.reject(
          log,
          ErrorCode.ACCESS_DENIED,
          "Only a super admin may manage admin accounts",
          "callerId",
          caller.id(),
          "targetId",
          target.getId(),
          "action",
          action);
    }
    branchAccess.requireBranchAdmin(caller, target.getBranchId(), action);
  }

  private String viewReason(Actor caller, User target) {
    if (caller.id().equals(target.getId())) {
      return "self";
    }
    if (caller.isSuperAdmin()) {
      return "super-admin";
    }
    if (caller.isAdmin() && target.getBranchId().equals(caller.branchId())) {
      return "branch-admin";
    }
    if (target instanceof Student
        && caller.isTeacher()
        && studentRelations.teachesStudent(caller.id(), target.getId())) {
      return "teacher-of-student";
    }
    if (target instanceof Student
        && caller.isGuardian()
        && studentGuardians.existsByGuardianIdAndStudentId(caller.id(), target.getId())) {
      return "guardian-of-student";
    }
    return null;
  }
}
