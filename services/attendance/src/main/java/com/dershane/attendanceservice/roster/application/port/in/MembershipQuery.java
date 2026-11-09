package com.dershane.attendanceservice.roster.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/** For other modules: membership questions about one student. */
public interface MembershipQuery {

  /** Whether the student's membership in the classroom of the branch covered {@code date}. */
  boolean wasMemberOn(UUID classroomId, UUID branchId, UUID studentId, LocalDate date);
}
