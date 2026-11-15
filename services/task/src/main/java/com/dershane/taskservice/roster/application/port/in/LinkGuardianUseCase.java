package com.dershane.taskservice.roster.application.port.in;

import com.dershane.taskservice.shared.application.Actor;
import java.time.Instant;
import java.util.UUID;

public interface LinkGuardianUseCase {

  /**
   * Sets the student's guardian, replacing a previous one. Admins of the student's branch (known
   * from the student's membership; without one only super admins). The SQS consumer will call this
   * port from {@code GuardianLinked}. A link whose {@code occurredAt} is older than the stored one
   * is a late event of a guardian already replaced and changes nothing.
   */
  void link(Actor actor, UUID guardianId, UUID studentId, Instant occurredAt);
}
