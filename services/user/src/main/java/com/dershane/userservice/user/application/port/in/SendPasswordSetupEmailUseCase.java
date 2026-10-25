package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface SendPasswordSetupEmailUseCase {

  /** Only for users with an e-mail address. */
  void send(Actor actor, UUID userId);
}
