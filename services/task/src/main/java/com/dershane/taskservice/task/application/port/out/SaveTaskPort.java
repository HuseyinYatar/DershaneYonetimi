package com.dershane.taskservice.task.application.port.out;

import com.dershane.taskservice.task.domain.Task;

public interface SaveTaskPort {

  /** Inserts a new task or updates a loaded one; returns it with id and current version. */
  Task save(Task task);
}
