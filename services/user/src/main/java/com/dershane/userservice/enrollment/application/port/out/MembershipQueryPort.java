package com.dershane.userservice.enrollment.application.port.out;

import com.dershane.userservice.enrollment.application.port.in.ClassroomStudentView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomSummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read side of memberships. Joins the student and classroom tables by entity name. */
public interface MembershipQueryPort {

  List<ClassroomStudentView> findActiveStudents(UUID classroomId);

  /** The classroom the student currently sits in. */
  Optional<ClassroomSummaryView> findClassroomOfStudent(UUID studentId);
}
