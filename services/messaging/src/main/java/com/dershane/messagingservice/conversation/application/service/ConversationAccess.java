package com.dershane.messagingservice.conversation.application.service;

import com.dershane.messagingservice.conversation.application.port.out.LoadConversationPort;
import com.dershane.messagingservice.conversation.domain.Conversation;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Loads a conversation and checks that the caller takes part in it. */
@Slf4j
@Component
@RequiredArgsConstructor
class ConversationAccess {

  private final LoadConversationPort conversations;

  Conversation requireParticipant(UUID conversationId, UUID userId) {
    Conversation conversation =
        conversations
            .findById(conversationId)
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.CONVERSATION_NOT_FOUND,
                        "Conversation does not exist",
                        "conversationId",
                        conversationId));
    if (!conversation.isParticipant(userId)) {
      throw Rejections.reject(
          log,
          ErrorCode.CONVERSATION_ACCESS_DENIED,
          "Caller is not a participant of the conversation",
          "conversationId",
          conversationId,
          "callerId",
          userId);
    }
    return conversation;
  }
}
