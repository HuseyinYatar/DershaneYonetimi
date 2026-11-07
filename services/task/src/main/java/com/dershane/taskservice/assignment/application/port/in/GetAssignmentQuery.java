package com.dershane.taskservice.assignment.application.port.in;

import com.dershane.taskservice.shared.application.Actor;
import java.util.UUID;

public interface GetAssignmentQuery {

  /** The student, their guardian, the task's teacher, a branch admin or a super admin. */
  AssignmentDetailView get(Actor actor, UUID assignmentId);
}
