package com.dershane.userservice.enrollment.application.port.out;

import com.dershane.userservice.enrollment.domain.ClassroomMembership;
import java.util.Optional;
import java.util.UUID;

public interface LoadMembershipPort {

  /** The student's open membership, if any (at most one exists). */
  Optional<ClassroomMembership> findActiveByStudentId(UUID studentId);
}
