package com.dershane.messagingservice.conversation.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface MessageSequencePort {

  /**
   * Gives the next number of the conversation with one atomic UPDATE. The row lock lasts until the
   * caller's transaction commits, so numbers are in commit order.
   */
  long nextSeq(UUID conversationId, Instant now);
}
