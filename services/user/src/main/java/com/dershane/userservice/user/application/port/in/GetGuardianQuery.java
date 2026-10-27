package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface GetGuardianQuery {

  GuardianView get(Actor actor, UUID guardianId);
}
