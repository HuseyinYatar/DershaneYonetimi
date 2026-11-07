package com.dershane.taskservice.assignment.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

public interface StudentJoinedClassroomUseCase {

  /**
   * Hands the student the open tasks of the classroom: today's occurrence of daily and weekly ones,
   * and one-off ones that have started and are not overdue. A membership that starts later is left
   * to the daily job.
   */
  void studentJoined(UUID studentId, UUID classroomId, LocalDate startDate);
}
