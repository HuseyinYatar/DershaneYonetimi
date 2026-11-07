package com.dershane.taskservice.assignment.application.port.in;

public interface GenerateTodayAssignmentsUseCase {

  /**
   * Hands out today's assignments of every active task that occurs today. Idempotent. A day the job
   * did not run is not made up for.
   */
  GenerateAssignmentsResult generateToday();
}
