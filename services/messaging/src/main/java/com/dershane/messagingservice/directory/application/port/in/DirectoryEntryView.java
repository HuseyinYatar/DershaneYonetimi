package com.dershane.messagingservice.directory.application.port.in;

import java.util.UUID;

/** Personal data (name): masked in {@code toString}. */
public record DirectoryEntryView(
    UUID userId,
    DirectoryRole role,
    UUID branchId,
    String firstName,
    String lastName,
    boolean active) {

  @Override
  public String toString() {
    return "DirectoryEntryView[userId=" + userId + "]";
  }
}
