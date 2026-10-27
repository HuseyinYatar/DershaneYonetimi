package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.user.application.port.out.StudentRelationsPort;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Tells the user module about a student's classroom and their teachers. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StudentRelationsAdapter implements StudentRelationsPort {

  private final MembershipJpaRepository memberships;
  private final TeacherAssignmentJpaRepository assignments;

  @Override
  public boolean teachesStudent(UUID teacherId, UUID studentId) {
    return assignments.teachesStudent(teacherId, studentId);
  }

  @Override
  public boolean hasActiveClassroom(UUID studentId) {
    return memberships.findByStudentIdAndEndDateIsNull(studentId).isPresent();
  }
}
