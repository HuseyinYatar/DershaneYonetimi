package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.List;
import java.util.UUID;

public interface ListGuardianChildrenQuery {

  /** Branch admins, or the guardian themself. */
  List<GuardianChildView> childrenOf(Actor actor, UUID guardianId);
}
