package com.dershane.taskservice.task.application.port.in;

import com.dershane.taskservice.shared.application.Actor;
import java.util.UUID;

public interface DeactivateTaskUseCase {

  /** Stops new assignments; existing ones stay. The task's teacher or an admin of its branch. */
  TaskView deactivate(Actor actor, UUID taskId);
}
