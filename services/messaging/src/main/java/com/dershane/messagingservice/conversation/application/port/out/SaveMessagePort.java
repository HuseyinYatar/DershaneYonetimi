package com.dershane.messagingservice.conversation.application.port.out;

import com.dershane.messagingservice.conversation.domain.Message;

public interface SaveMessagePort {

  /** Inserts or updates the message and returns it as stored (with its {@code updatedAt}). */
  Message save(Message message);
}
