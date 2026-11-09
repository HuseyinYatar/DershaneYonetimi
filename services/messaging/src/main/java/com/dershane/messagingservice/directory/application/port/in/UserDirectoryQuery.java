package com.dershane.messagingservice.directory.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Who is this user? Answers from the local copy, never from the user service. */
public interface UserDirectoryQuery {

  Optional<DirectoryEntryView> find(UUID userId);
}
