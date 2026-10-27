package com.dershane.userservice.user.application.service;

import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Role;
import java.util.UUID;

/** What {@link UserAccountService#create} needs to open a login account. */
record NewAccount(Role role, UUID branchId, PersonalInfo info, String username) {

  @Override
  public String toString() {
    return "NewAccount[role=" + role + ", branchId=" + branchId + "]";
  }
}
