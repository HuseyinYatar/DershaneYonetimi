package com.dershane.userservice.user.domain;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Common user data (diagram: Kullanici). Students, teachers and guardians extend it; an admin is a
 * plain {@code User} with role ADMIN.
 *
 * <p>{@code toString} prints only the id: names and contact details are personal data and must not
 * end up in logs.
 */
@Slf4j
@Getter
public class User {

  /** Keycloak {@code sub}. */
  private final UUID id;

  /** Version the user was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID branchId;
  private final Role role;
  private PersonalInfo info;
  private final String username;
  private boolean active;

  /** Rehydrates a saved user. Use {@link #admin} and the {@code register} factories otherwise. */
  public User(
      UUID id,
      Long version,
      UUID branchId,
      Role role,
      PersonalInfo info,
      String username,
      boolean active) {
    this.id = id;
    this.version = version;
    this.branchId = branchId;
    this.role = role;
    this.info = info;
    this.username = username;
    this.active = active;
  }

  public static User admin(UUID id, UUID branchId, PersonalInfo info, String username) {
    return new User(id, null, branchId, Role.ADMIN, info, username, true);
  }

  public String fullName() {
    return info.firstName() + " " + info.lastName();
  }

  public PersonalInfo personalInfo() {
    return info;
  }

  public void applyPersonalInfo(PersonalInfo newInfo) {
    this.info = newInfo;
  }

  public void deactivate() {
    this.active = false;
  }

  public void requireActive(String action) {
    if (!active) {
      throw Rejections.reject(
          log,
          ErrorCode.USER_INACTIVE,
          "Operation rejected: user is inactive",
          "userId",
          id,
          "action",
          action);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[id=" + id + "]";
  }
}
