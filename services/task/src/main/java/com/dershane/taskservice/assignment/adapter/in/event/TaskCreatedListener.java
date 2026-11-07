package com.dershane.taskservice.assignment.adapter.in.event;

import com.dershane.taskservice.assignment.application.port.in.GenerateForNewTaskUseCase;
import com.dershane.taskservice.task.application.event.TaskCreated;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Hands out today's assignments of a new task. The event is published inside the creating
 * transaction and handled synchronously, so the task and its first assignments commit together.
 */
@Component
@RequiredArgsConstructor
class TaskCreatedListener {

  private final GenerateForNewTaskUseCase generate;

  @EventListener
  void onTaskCreated(TaskCreated event) {
    generate.generateFor(event.taskId());
  }
}
