package com.dershane.taskservice.assignment.application.port.in;

import com.dershane.taskservice.shared.application.Actor;
import java.util.UUID;

public interface SubmitAssignmentUseCase {

  /** Only the assignment's student. Replaces an earlier submission until it is evaluated. */
  AssignmentDetailView submit(Actor actor, UUID assignmentId, SubmitAssignmentCommand command);
}
