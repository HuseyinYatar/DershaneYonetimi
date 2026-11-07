package com.dershane.taskservice.task.application.port.in;

import com.dershane.taskservice.shared.application.Actor;
import java.util.UUID;

public interface GetTaskQuery {

  /** The task's teacher or an admin of its branch. */
  TaskView get(Actor actor, UUID taskId);

  /**
   * Checks that the caller may look at the task and its assignments (the task's teacher, an admin
   * of its branch or a super admin).
   */
  void requireManager(Actor actor, UUID taskId);
}
