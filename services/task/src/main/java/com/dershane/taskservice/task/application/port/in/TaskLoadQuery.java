package com.dershane.taskservice.task.application.port.in;

import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** For other modules: the task aggregates themselves. No caller check; the caller decides. */
public interface TaskLoadQuery {

  Optional<Task> findById(UUID taskId);

  /**
   * Active tasks whose date range contains {@code date}; the caller still asks {@code occursOn}.
   */
  List<Task> findActiveOn(LocalDate date);

  /** Active tasks given to the target whose date range contains {@code date}. */
  List<Task> findActiveTargeting(TargetType targetType, UUID targetId, LocalDate date);
}
