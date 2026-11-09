package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;
import java.util.UUID;

public interface MarkReadUseCase {

  /** Moves the caller's read position forward to {@code seq}; never backwards. */
  void markRead(Actor actor, UUID conversationId, long seq);
}
