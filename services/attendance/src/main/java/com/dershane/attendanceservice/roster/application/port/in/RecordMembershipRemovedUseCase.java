package com.dershane.attendanceservice.roster.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.time.LocalDate;
import java.util.UUID;

public interface RecordMembershipRemovedUseCase {

  /**
   * Closes a membership on {@code endDate} (inclusive); repeating it changes nothing. Admins of the
   * membership's branch (and super admins). The SQS consumer will call this port from {@code
   * StudentRemovedFromClassroom}.
   */
  void recordRemoved(Actor actor, UUID membershipId, LocalDate endDate);
}
