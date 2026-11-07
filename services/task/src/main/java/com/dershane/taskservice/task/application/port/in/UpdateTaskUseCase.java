package com.dershane.taskservice.task.application.port.in;

import com.dershane.taskservice.shared.application.Actor;
import java.util.UUID;

public interface UpdateTaskUseCase {

  /** The task's teacher or an admin of its branch. */
  TaskView update(Actor actor, UUID taskId, UpdateTaskCommand command);
}
