package com.dershane.userservice.enrollment.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Used when a student is deactivated; runs inside the caller's transaction. */
public interface CloseActiveMembershipUseCase {

  /** Returns the id of the membership that was closed, if the student had one. */
  Optional<UUID> closeActiveMembership(UUID studentId, String action);
}
