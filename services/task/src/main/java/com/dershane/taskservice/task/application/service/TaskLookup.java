package com.dershane.taskservice.task.application.service;

import com.dershane.taskservice.task.application.port.in.TaskLoadQuery;
import com.dershane.taskservice.task.application.port.out.LoadTaskPort;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-only answers other modules ask about tasks. */
@Component
@RequiredArgsConstructor
class TaskLookup implements TaskLoadQuery {

  private final LoadTaskPort loadTask;

  @Override
  @Transactional(readOnly = true)
  public Optional<Task> findById(UUID taskId) {
    return loadTask.findById(taskId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Task> findActiveOn(LocalDate date) {
    return loadTask.findActiveOn(date);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Task> findActiveTargeting(TargetType targetType, UUID targetId, LocalDate date) {
    return loadTask.findActiveTargeting(targetType, targetId, date);
  }
}
