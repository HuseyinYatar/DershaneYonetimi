package com.dershane.userservice.user.application.service;

import com.dershane.userservice.user.domain.User;

/**
 * @param temporaryPassword set only for accounts without e-mail; return it to the admin once
 */
record AccountCreated<T extends User>(
    T user, String temporaryPassword, boolean passwordSetupEmailSent) {

  @Override
  public String toString() {
    return "AccountCreated[user=" + user + "]";
  }
}
