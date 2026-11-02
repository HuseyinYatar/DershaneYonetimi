package com.dershane.attendanceservice.session.application.service;

import com.dershane.attendanceservice.session.application.port.in.SessionAccessGuard;
import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SessionAccessPolicy implements SessionAccessGuard {

  private final BranchAccessPolicy branchAccess;

  @Override
  public void requireStaffOf(Actor actor, ClassSession session, String action) {
    if (actor.isTeacher() && session.isTeacher(actor.id())) {
      log.atDebug()
          .addKeyValue("callerId", actor.id())
          .addKeyValue("sessionId", session.getId())
          .addKeyValue("action", action)
          .log("Session access allowed for its teacher");
      return;
    }
    if (actor.isAdmin() || actor.isSuperAdmin()) {
      branchAccess.requireBranchAdmin(actor, session.getBranchId(), action);
      return;
    }
    if (actor.isTeacher()) {
      throw Rejections.reject(
          log,
          ErrorCode.NOT_SESSION_TEACHER,
          "Caller is a teacher but not the teacher of this session",
          "callerId",
          actor.id(),
          "sessionId",
          session.getId(),
          "action",
          action);
    }
    throw Rejections.reject(
        log,
        ErrorCode.ACCESS_DENIED,
        "Caller is neither an admin nor a teacher",
        "callerId",
        actor.id(),
        "sessionId",
        session.getId(),
        "action",
        action);
  }

  @Override
  public boolean actsAsAdmin(Actor actor, ClassSession session) {
    if (actor.isSuperAdmin()) {
      return true;
    }
    // A missing branch claim must never match: null == null would open every branch.
    return actor.isAdmin()
        && actor.branchId() != null
        && actor.branchId().equals(session.getBranchId());
  }
}
