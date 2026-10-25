package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface GetUserQuery {

  /**
   * Allowed: the user themself, a super admin, an admin of the user's branch, a teacher of the
   * student's current classroom, the student's guardian.
   */
  UserView get(Actor actor, UUID userId);
}
