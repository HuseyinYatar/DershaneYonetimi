package com.dershane.userservice.user.domain;

import java.util.UUID;

/** Diagram: Veli. Children are found through {@link StudentGuardian}. */
public class Guardian extends User {

  /** Rehydrates a saved guardian. Use {@link #register} for a new one. */
  public Guardian(
      UUID id, Long version, UUID branchId, PersonalInfo info, String username, boolean active) {
    super(id, version, branchId, Role.GUARDIAN, info, username, active);
  }

  public static Guardian register(UUID id, UUID branchId, PersonalInfo info, String username) {
    return new Guardian(id, null, branchId, info, username, true);
  }
}
