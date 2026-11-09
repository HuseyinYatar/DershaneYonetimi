package com.dershane.taskservice.roster.application.port.in;

import com.dershane.taskservice.shared.application.Actor;
import java.time.Instant;
import java.util.UUID;

public interface UnlinkGuardianUseCase {

  /**
   * Removes the link only while the student is still linked to {@code guardianId}. An event about a
   * guardian who was already replaced, or about a link that was made again after {@code
   * occurredAt}, changes nothing. When no link is recorded yet the matching {@code GuardianLinked}
   * has not arrived (events are not ordered): the call is rejected so the message is retried.
   */
  void unlink(Actor actor, UUID studentId, UUID guardianId, Instant occurredAt);
}
