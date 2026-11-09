package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ConversationQuery {

  /** The caller's conversations, most recent message first. */
  Page<ConversationView> list(Actor actor, Pageable pageable);

  ConversationView get(Actor actor, UUID conversationId);
}
