package com.dershane.messagingservice.conversation.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Append-only trail of earlier message texts. */
public interface MessageEditPort {

  void append(UUID messageId, String previousContent, Instant editedAt);
}
