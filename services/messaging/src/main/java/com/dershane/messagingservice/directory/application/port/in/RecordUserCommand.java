package com.dershane.messagingservice.directory.application.port.in;

import java.util.UUID;

/**
 * State of a user as announced by {@code UserUpdated}. Personal data (name): masked in {@code
 * toString}.
 *
 * @param role the raw role name; roles other than student and teacher are skipped
 */
public record RecordUserCommand(
    UUID userId,
    String role,
    UUID branchId,
    String firstName,
    String lastName,
    boolean active,
    long recordVersion) {

  @Override
  public String toString() {
    return "RecordUserCommand[userId=" + userId + ", recordVersion=" + recordVersion + "]";
  }
}
