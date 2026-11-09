package com.dershane.messagingservice.directory.domain;

import java.util.UUID;
import lombok.Getter;

/**
 * A student or teacher as the user service last announced them. {@code recordVersion} is the user
 * service's own counter: an event older than the stored one is stale and ignored.
 */
@Getter
public class UserReplica {

  private final UUID userId;

  /** Version the replica was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UserRole role;
  private final UUID branchId;
  private final long recordVersion;
  private final boolean active;

  // Personal data: never logged, masked in toString.
  private final String firstName;
  private final String lastName;

  @SuppressWarnings("checkstyle:ParameterNumber")
  public UserReplica(
      UUID userId,
      Long version,
      UserRole role,
      UUID branchId,
      String firstName,
      String lastName,
      boolean active,
      long recordVersion) {
    this.userId = userId;
    this.version = version;
    this.role = role;
    this.branchId = branchId;
    this.firstName = firstName;
    this.lastName = lastName;
    this.active = active;
    this.recordVersion = recordVersion;
  }

  /** True when an event with {@code incomingVersion} carries newer state than this copy. */
  public boolean isOlderThan(long incomingVersion) {
    return recordVersion < incomingVersion;
  }

  @Override
  public String toString() {
    return "UserReplica[userId=" + userId + ", recordVersion=" + recordVersion + "]";
  }
}
