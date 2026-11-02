package com.dershane.attendanceservice.session.application.port.in;

import java.util.UUID;

public interface CompleteSessionUseCase {

  /**
   * Marks attendance as taken. The caller (the attendance module) already checked who may do it and
   * calls this inside its own transaction.
   */
  void complete(UUID sessionId, UUID takenBy);
}
