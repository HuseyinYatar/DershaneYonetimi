package com.dershane.messagingservice.conversation.adapter.in.web.dto;

import com.dershane.messagingservice.conversation.domain.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Free text: masked in {@code toString}. */
public record SendMessageRequest(
    @NotNull MessageType type, @Size(max = 4000) String content, UUID fileId) {

  @Override
  public String toString() {
    return "SendMessageRequest[type=" + type + "]";
  }
}
