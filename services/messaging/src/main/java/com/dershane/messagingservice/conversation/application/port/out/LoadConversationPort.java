package com.dershane.messagingservice.conversation.application.port.out;

import com.dershane.messagingservice.conversation.domain.Conversation;
import java.util.Optional;
import java.util.UUID;

public interface LoadConversationPort {

  Optional<Conversation> findById(UUID conversationId);

  Optional<Conversation> findByPair(UUID studentId, UUID teacherId);
}
