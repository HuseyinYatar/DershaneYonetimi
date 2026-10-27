package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface LinkGuardianUseCase {

  /** A student has one guardian; linking another guardian replaces the old link. */
  StudentGuardianView link(Actor actor, UUID studentId, LinkGuardianCommand command);
}
