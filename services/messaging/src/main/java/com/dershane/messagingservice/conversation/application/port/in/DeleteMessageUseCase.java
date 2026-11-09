package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;
import java.util.UUID;

public interface DeleteMessageUseCase {

  void delete(Actor actor, UUID conversationId, UUID messageId);
}
