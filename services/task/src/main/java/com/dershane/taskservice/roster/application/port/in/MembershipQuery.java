package com.dershane.taskservice.roster.application.port.in;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** For other modules: membership questions. No caller check; the caller decides. */
public interface MembershipQuery {

  /** Whether the student's membership in the classroom covered {@code date}. */
  boolean wasMemberOn(UUID classroomId, UUID studentId, LocalDate date);

  /** The branch of a classroom, known from any membership that covered {@code date}. */
  Optional<UUID> branchOfClassroomOn(UUID classroomId, LocalDate date);

  /** The student's membership that covered {@code date}, if any. */
  Optional<ActiveMembershipView> activeMembershipOf(UUID studentId, LocalDate date);
}
