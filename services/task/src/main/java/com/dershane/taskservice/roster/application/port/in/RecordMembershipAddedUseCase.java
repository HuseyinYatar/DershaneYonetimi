package com.dershane.taskservice.roster.application.port.in;

import com.dershane.taskservice.shared.application.Actor;

public interface RecordMembershipAddedUseCase {

  /**
   * Stores a student's membership; repeating it with the same id changes nothing. Admins of the
   * membership's branch (and super admins). The SQS consumer will call this port from {@code
   * StudentAddedToClassroom}.
   */
  void recordAdded(Actor actor, RecordMembershipAddedCommand command);
}
