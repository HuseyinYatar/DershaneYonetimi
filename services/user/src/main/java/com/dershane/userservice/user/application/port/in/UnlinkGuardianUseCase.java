package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface UnlinkGuardianUseCase {

  void unlink(Actor actor, UUID studentId);
}
