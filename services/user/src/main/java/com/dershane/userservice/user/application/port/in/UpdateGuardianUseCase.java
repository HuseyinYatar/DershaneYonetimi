package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface UpdateGuardianUseCase {

  GuardianView update(Actor actor, UUID guardianId, UpdateGuardianCommand command);
}
