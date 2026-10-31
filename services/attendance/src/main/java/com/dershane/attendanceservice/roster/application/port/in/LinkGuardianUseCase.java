package com.dershane.attendanceservice.roster.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface LinkGuardianUseCase {

  /**
   * Sets the student's guardian, replacing a previous one. Admins of the student's branch (known
   * from the student's membership; without one only super admins). The SQS consumer will call this
   * port from {@code GuardianLinked}.
   */
  void link(Actor actor, UUID guardianId, UUID studentId);
}
