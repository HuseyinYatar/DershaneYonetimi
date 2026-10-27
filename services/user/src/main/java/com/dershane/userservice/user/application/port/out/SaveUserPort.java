package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.domain.User;

public interface SaveUserPort {

  /**
   * Inserts a new user or updates a loaded one and flushes, so the returned user carries the final
   * {@code version} (consumers of {@code UserUpdated} use it to order updates).
   */
  <T extends User> T save(T user);
}
