package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.conversation.domain.MessageType;
import java.util.UUID;

/** Free text: masked in {@code toString}. */
public record SendMessageCommand(MessageType type, String content, UUID fileId) {

  @Override
  public String toString() {
    return "SendMessageCommand[type=" + type + "]";
  }
}
