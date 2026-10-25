package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.enrollment.application.port.in.ClassroomStudentView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomSummaryView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomTeacherView;
import com.dershane.userservice.enrollment.application.port.in.TeacherAssignmentView;
import com.dershane.userservice.enrollment.application.port.out.MembershipQueryPort;
import com.dershane.userservice.enrollment.application.port.out.TeacherAssignmentQueryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read side of enrollment: views straight from the tables. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class EnrollmentQueryAdapter implements MembershipQueryPort, TeacherAssignmentQueryPort {

  private final MembershipJpaRepository memberships;
  private final TeacherAssignmentJpaRepository assignments;

  @Override
  public List<ClassroomStudentView> findActiveStudents(UUID classroomId) {
    return memberships.findActiveStudents(classroomId);
  }

  @Override
  public Optional<ClassroomSummaryView> findClassroomOfStudent(UUID studentId) {
    return memberships.findClassroomOfStudent(studentId);
  }

  @Override
  public Optional<TeacherAssignmentView> findViewById(UUID assignmentId) {
    return assignments.findAssignmentView(assignmentId);
  }

  @Override
  public List<TeacherAssignmentView> findAssignmentsOfTeacher(UUID teacherId) {
    return assignments.findAssignmentsOfTeacher(teacherId);
  }

  @Override
  public List<ClassroomTeacherView> findTeachersOfClassroom(UUID classroomId) {
    return assignments.findTeachersOfClassroom(classroomId);
  }

  @Override
  public List<ClassroomSummaryView> findActiveClassroomsOfTeacher(UUID teacherId) {
    return assignments.findActiveClassroomsOfTeacher(teacherId);
  }
}
