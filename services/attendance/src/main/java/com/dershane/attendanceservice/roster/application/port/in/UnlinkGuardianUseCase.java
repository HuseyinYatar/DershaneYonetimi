package com.dershane.attendanceservice.roster.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface UnlinkGuardianUseCase {

  /** Removes the student's guardian link; same access as {@link LinkGuardianUseCase}. */
  void unlink(Actor actor, UUID studentId);
}
