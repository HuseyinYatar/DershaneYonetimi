package com.dershane.taskservice.assignment.application.port.in;

public interface ProcessDeadlinesUseCase {

  /** Sends due-soon reminders first, then marks assignments whose due time passed as overdue. */
  DeadlineResult processDeadlines();

  /**
   * @param reminded assignments that got a {@code TaskDueSoon} event
   * @param overdue assignments marked overdue (each got a {@code TaskOverdue} event)
   */
  record DeadlineResult(int reminded, int overdue) {}
}
