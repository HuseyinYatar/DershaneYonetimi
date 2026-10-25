package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface DeactivateUserUseCase {

  /**
   * Disables the login first, then marks the profile inactive. A student's active classroom
   * membership is closed by the enrollment module. Records are kept.
   */
  UserView deactivate(Actor actor, UUID userId);
}
