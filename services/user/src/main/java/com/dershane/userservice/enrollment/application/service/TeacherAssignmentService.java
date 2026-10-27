package com.dershane.userservice.enrollment.application.service;

import com.dershane.userservice.classroom.application.port.in.LoadClassroomQuery;
import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.enrollment.application.port.in.AssignTeacherCommand;
import com.dershane.userservice.enrollment.application.port.in.AssignTeacherUseCase;
import com.dershane.userservice.enrollment.application.port.in.ListTeacherAssignmentsQuery;
import com.dershane.userservice.enrollment.application.port.in.RemoveTeacherAssignmentUseCase;
import com.dershane.userservice.enrollment.application.port.in.TeacherAssignmentView;
import com.dershane.userservice.enrollment.application.port.out.LoadTeacherAssignmentPort;
import com.dershane.userservice.enrollment.application.port.out.SaveTeacherAssignmentPort;
import com.dershane.userservice.enrollment.application.port.out.TeacherAssignmentQueryPort;
import com.dershane.userservice.enrollment.domain.EnrollmentPolicy;
import com.dershane.userservice.enrollment.domain.TeacherAssignment;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.subject.application.port.in.LoadSubjectQuery;
import com.dershane.userservice.subject.domain.Subject;
import com.dershane.userservice.user.application.port.in.LoadUserQuery;
import com.dershane.userservice.user.domain.Teacher;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class TeacherAssignmentService
    implements AssignTeacherUseCase, RemoveTeacherAssignmentUseCase, ListTeacherAssignmentsQuery {

  private static final String CREATE = "teacher.assignment.create";
  private static final String DELETE = "teacher.assignment.delete";

  private final LoadTeacherAssignmentPort loadAssignment;
  private final SaveTeacherAssignmentPort saveAssignment;
  private final TeacherAssignmentQueryPort assignmentQuery;
  private final LoadUserQuery userLookup;
  private final LoadClassroomQuery classroomLookup;
  private final LoadSubjectQuery subjectLookup;
  private final BranchAccessPolicy branchAccess;

  @Override
  @Transactional
  @Observed(name = CREATE)
  public TeacherAssignmentView assign(Actor actor, UUID teacherId, AssignTeacherCommand command) {
    Teacher teacher = userLookup.requireTeacher(teacherId);
    branchAccess.requireBranchAdmin(actor, teacher.getBranchId(), CREATE);
    Classroom classroom = classroomLookup.require(command.classroomId());
    Subject subject = subjectLookup.require(command.subjectId());
    EnrollmentPolicy.requireCanAssign(teacher, classroom, subject, CREATE);
    if (loadAssignment.exists(teacherId, classroom.getId(), subject.getId())) {
      throw Rejections.reject(
          log,
          ErrorCode.TEACHER_ASSIGNMENT_EXISTS,
          "Teacher already teaches this subject in this classroom",
          "teacherId",
          teacherId,
          "classroomId",
          classroom.getId(),
          "subjectId",
          subject.getId());
    }
    TeacherAssignment assignment =
        saveAssignment.save(
            TeacherAssignment.create(teacherId, classroom.getId(), subject.getId()));
    log.atInfo()
        .addKeyValue("assignmentId", assignment.getId())
        .addKeyValue("teacherId", teacherId)
        .addKeyValue("classroomId", classroom.getId())
        .addKeyValue("subjectId", subject.getId())
        .log("Teacher assigned to classroom");
    return assignmentQuery
        .findViewById(assignment.getId())
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.TEACHER_ASSIGNMENT_NOT_FOUND,
                    "Teacher assignment not found",
                    "assignmentId",
                    assignment.getId()));
  }

  @Override
  @Transactional
  @Observed(name = DELETE)
  public void remove(Actor actor, UUID teacherId, UUID assignmentId) {
    Teacher teacher = userLookup.requireTeacher(teacherId);
    branchAccess.requireBranchAdmin(actor, teacher.getBranchId(), DELETE);
    TeacherAssignment assignment =
        loadAssignment
            .findByIdAndTeacherId(assignmentId, teacherId)
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.TEACHER_ASSIGNMENT_NOT_FOUND,
                        "Teacher assignment not found",
                        "teacherId",
                        teacherId,
                        "assignmentId",
                        assignmentId));
    saveAssignment.delete(assignment);
    log.atInfo()
        .addKeyValue("assignmentId", assignmentId)
        .addKeyValue("teacherId", teacherId)
        .log("Teacher assignment removed");
  }

  /** Branch admins, or the teacher themself. */
  @Override
  @Transactional(readOnly = true)
  public List<TeacherAssignmentView> list(Actor actor, UUID teacherId) {
    Teacher teacher = userLookup.requireTeacher(teacherId);
    if (actor.id().equals(teacherId)) {
      log.atDebug().addKeyValue("teacherId", teacherId).log("Teacher listing own assignments");
    } else {
      branchAccess.requireBranchAdmin(actor, teacher.getBranchId(), "teacher.assignment.list");
    }
    return assignmentQuery.findAssignmentsOfTeacher(teacherId);
  }
}
