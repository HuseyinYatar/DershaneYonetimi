package com.dershane.attendanceservice.roster.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.util.UUID;

public interface UnlinkGuardianUseCase {

  /**
   * Removes the link only while the student is still linked to {@code guardianId}. An event about a
   * guardian who was already replaced changes nothing.
   */
  void unlink(Actor actor, UUID studentId, UUID guardianId);
}
