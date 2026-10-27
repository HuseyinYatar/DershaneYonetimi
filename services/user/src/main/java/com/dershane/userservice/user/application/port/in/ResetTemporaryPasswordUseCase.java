package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface ResetTemporaryPasswordUseCase {

  /** Only for users without e-mail; users with e-mail reset their password themselves. */
  TemporaryPasswordResult reset(Actor actor, UUID userId);
}
