package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;

public interface RegisterGuardianUseCase {

  /** Admins of the target branch. Without e-mail the phone number becomes the username. */
  RegistrationResult<GuardianView> register(Actor actor, RegisterGuardianCommand command);
}
