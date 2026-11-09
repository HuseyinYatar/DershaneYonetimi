package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;
import java.util.UUID;

/** Opens the conversation with another user, or returns the existing one. */
public interface OpenConversationUseCase {

  OpenedConversation open(Actor actor, UUID participantId);
}
