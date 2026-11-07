package com.dershane.taskservice.assignment.application.port.in;

import com.dershane.taskservice.shared.application.Actor;
import java.util.UUID;

public interface EvaluateAssignmentUseCase {

  /** Only the task's teacher. Evaluating again overwrites the earlier verdict. */
  AssignmentDetailView evaluate(Actor actor, UUID assignmentId, EvaluateAssignmentCommand command);
}
