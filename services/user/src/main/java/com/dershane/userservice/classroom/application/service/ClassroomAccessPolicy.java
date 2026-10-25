package com.dershane.userservice.classroom.application.service;

import com.dershane.userservice.classroom.application.port.in.ClassroomAccessGuard;
import com.dershane.userservice.classroom.application.port.out.ClassroomEnrollmentPort;
import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ClassroomAccessPolicy implements ClassroomAccessGuard {

  private final ClassroomEnrollmentPort enrollment;
  private final BranchAccessPolicy branchAccess;

  /** Branch admins, teachers of the classroom and its current students. */
  @Override
  public void requireCanView(Actor actor, Classroom classroom) {
    if (actor.isStudent() && enrollment.isStudentOf(actor.id(), classroom.getId())) {
      log.atDebug()
          .addKeyValue("callerId", actor.id())
          .addKeyValue("classroomId", classroom.getId())
          .log("Classroom view allowed for its student");
      return;
    }
    requireStaffOf(actor, classroom, "classroom.view");
  }

  /** Student lists are visible to branch admins and the classroom's teachers only. */
  @Override
  public void requireStaffOf(Actor actor, Classroom classroom, String action) {
    if (actor.isTeacher() && enrollment.isTeacherOf(actor.id(), classroom.getId())) {
      log.atDebug()
          .addKeyValue("callerId", actor.id())
          .addKeyValue("classroomId", classroom.getId())
          .addKeyValue("action", action)
          .log("Classroom access allowed for its teacher");
      return;
    }
    if (actor.isAdmin() || actor.isSuperAdmin()) {
      branchAccess.requireBranchAdmin(actor, classroom.getBranchId(), action);
      return;
    }
    throw Rejections.reject(
        log,
        ErrorCode.ACCESS_DENIED,
        "Caller is neither an admin nor a teacher of this classroom",
        "callerId",
        actor.id(),
        "classroomId",
        classroom.getId(),
        "action",
        action);
  }
}
