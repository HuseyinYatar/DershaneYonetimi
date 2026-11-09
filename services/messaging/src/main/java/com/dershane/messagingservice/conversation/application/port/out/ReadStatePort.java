package com.dershane.messagingservice.conversation.application.port.out;

import java.util.UUID;

public interface ReadStatePort {

  /**
   * Raises the participant's read position to {@code seq} if that is higher.
   *
   * @return true when the stored position changed
   */
  boolean advance(UUID conversationId, UUID userId, long seq);
}
