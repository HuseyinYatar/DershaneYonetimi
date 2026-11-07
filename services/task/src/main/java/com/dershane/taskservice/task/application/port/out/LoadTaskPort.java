package com.dershane.taskservice.task.application.port.out;

import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadTaskPort {

  Optional<Task> findById(UUID taskId);

  /** Active tasks with {@code startDate <= date <= endDate|infinity}. */
  List<Task> findActiveOn(LocalDate date);

  /** Same range rule, limited to one target. */
  List<Task> findActiveTargeting(TargetType targetType, UUID targetId, LocalDate date);
}
