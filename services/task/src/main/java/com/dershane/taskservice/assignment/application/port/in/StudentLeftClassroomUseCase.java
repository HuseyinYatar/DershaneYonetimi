package com.dershane.taskservice.assignment.application.port.in;

import java.util.UUID;

public interface StudentLeftClassroomUseCase {

  /**
   * Cancels the student's open assignments (not overdue, not submitted) of tasks given to the
   * classroom. Overdue, submitted and graded ones and individually given tasks stay.
   */
  void studentLeft(UUID studentId, UUID classroomId);
}
