package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.classroom.application.port.out.ClassroomEnrollmentPort;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tells the classroom module who sits in and who teaches a classroom. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ClassroomEnrollmentAdapter implements ClassroomEnrollmentPort {

  private final MembershipJpaRepository memberships;
  private final TeacherAssignmentJpaRepository assignments;

  @Override
  public boolean hasActiveStudents(UUID classroomId) {
    return memberships.existsByClassroomIdAndEndDateIsNull(classroomId);
  }

  @Override
  public long countActiveStudents(UUID classroomId) {
    return memberships.countByClassroomIdAndEndDateIsNull(classroomId);
  }

  @Override
  public boolean isStudentOf(UUID studentId, UUID classroomId) {
    return memberships.existsByStudentIdAndClassroomIdAndEndDateIsNull(studentId, classroomId);
  }

  @Override
  public boolean isTeacherOf(UUID teacherId, UUID classroomId) {
    return assignments.existsByTeacherIdAndClassroomId(teacherId, classroomId);
  }
}
