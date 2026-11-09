package com.dershane.messagingservice.conversation.application.port.out;

import com.dershane.messagingservice.conversation.domain.Conversation;

public interface SaveConversationPort {

  /**
   * Inserts a new conversation with its two participant rows.
   *
   * @return false when the pair already has a conversation (a concurrent open won); nothing was
   *     written
   */
  boolean insert(Conversation conversation);
}
