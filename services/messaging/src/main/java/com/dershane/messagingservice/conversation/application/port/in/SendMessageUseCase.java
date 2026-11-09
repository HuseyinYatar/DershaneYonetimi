package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;
import java.util.UUID;

public interface SendMessageUseCase {

  MessageView send(Actor actor, UUID conversationId, SendMessageCommand command);
}
