package com.dershane.taskservice.task.application.port.in;

import com.dershane.taskservice.shared.application.Actor;

public interface CreateTaskUseCase {

  /** Teachers only; the target must be active in the teacher's branch. */
  TaskView create(Actor actor, CreateTaskCommand command);
}
