package com.dershane.userservice.branch.domain;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** A site of the institution (diagram: Sube). */
@Slf4j
@Getter
public class Branch {

  /** Null until the branch is saved for the first time. */
  private final UUID id;

  /** Version the branch was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private String name;

  /** Short unique code, used in usernames of students without e-mail ({@code MRKZ-1042}). */
  private final String code;

  private boolean active;

  /** Rehydrates a saved branch. Use {@link #create} for a new one. */
  public Branch(UUID id, Long version, String name, String code, boolean active) {
    this.id = id;
    this.version = version;
    this.name = name;
    this.code = code;
    this.active = active;
  }

  public static Branch create(String name, String code) {
    return new Branch(null, null, name, code, true);
  }

  public void rename(String newName) {
    this.name = newName;
  }

  /**
   * Deactivates the branch unless it still has active classrooms.
   *
   * @return false when the branch was already inactive (nothing changed)
   */
  public boolean deactivate(boolean hasActiveClassrooms) {
    if (!active) {
      return false;
    }
    if (hasActiveClassrooms) {
      throw Rejections.reject(
          log,
          ErrorCode.BRANCH_HAS_ACTIVE_CLASSROOMS,
          "Branch deactivation rejected: branch still has active classrooms",
          "branchId",
          id);
    }
    this.active = false;
    return true;
  }

  public void requireActive(String action) {
    if (!active) {
      throw Rejections.reject(
          log,
          ErrorCode.BRANCH_INACTIVE,
          "Operation rejected: branch is inactive",
          "branchId",
          id,
          "action",
          action);
    }
  }
}
