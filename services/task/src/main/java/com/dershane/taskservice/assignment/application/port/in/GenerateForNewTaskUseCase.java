package com.dershane.taskservice.assignment.application.port.in;

import java.util.UUID;

public interface GenerateForNewTaskUseCase {

  /** Hands out today's assignments of a task that was just created, if it occurs today. */
  void generateFor(UUID taskId);
}
