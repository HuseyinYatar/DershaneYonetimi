package com.dershane.userservice.enrollment.application.service;

import com.dershane.userservice.classroom.application.port.in.ClassroomAccessGuard;
import com.dershane.userservice.classroom.application.port.in.LoadClassroomQuery;
import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.enrollment.application.port.in.ClassroomStudentView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomSummaryView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomTeacherView;
import com.dershane.userservice.enrollment.application.port.in.ListClassroomStudentsQuery;
import com.dershane.userservice.enrollment.application.port.in.ListClassroomTeachersQuery;
import com.dershane.userservice.enrollment.application.port.in.ListMyClassroomsQuery;
import com.dershane.userservice.enrollment.application.port.out.MembershipQueryPort;
import com.dershane.userservice.enrollment.application.port.out.TeacherAssignmentQueryPort;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who is in a classroom, who teaches it, and which classrooms the caller belongs to. */
@Slf4j
@Service
@RequiredArgsConstructor
class ClassroomRosterService
    implements ListClassroomStudentsQuery, ListClassroomTeachersQuery, ListMyClassroomsQuery {

  private final MembershipQueryPort membershipQuery;
  private final TeacherAssignmentQueryPort assignmentQuery;
  private final LoadClassroomQuery classroomLookup;
  private final ClassroomAccessGuard classroomAccess;

  @Override
  @Transactional(readOnly = true)
  public List<ClassroomStudentView> students(Actor actor, UUID classroomId) {
    Classroom classroom = classroomLookup.require(classroomId);
    classroomAccess.requireStaffOf(actor, classroom, "classroom.students");
    return membershipQuery.findActiveStudents(classroomId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClassroomTeacherView> teachers(Actor actor, UUID classroomId) {
    Classroom classroom = classroomLookup.require(classroomId);
    classroomAccess.requireCanView(actor, classroom);
    return assignmentQuery.findTeachersOfClassroom(classroomId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClassroomSummaryView> myClassrooms(Actor actor) {
    if (!actor.isTeacher() && !actor.isStudent()) {
      throw Rejections.reject(
          log,
          ErrorCode.ACCESS_DENIED,
          "Endpoint not available for the caller's role",
          "callerId",
          actor.id(),
          "action",
          "me.classrooms");
    }
    if (actor.isTeacher()) {
      return assignmentQuery.findActiveClassroomsOfTeacher(actor.id());
    }
    return membershipQuery.findClassroomOfStudent(actor.id()).stream().toList();
  }
}
