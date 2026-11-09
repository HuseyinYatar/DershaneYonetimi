package com.dershane.messagingservice.conversation.application.service;

import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.domain.Message;

final class MessageViews {

  private MessageViews() {}

  /** A deleted message keeps its row but loses its content and file in every client view. */
  static MessageView of(Message m) {
    return new MessageView(
        m.getId(),
        m.getConversationId(),
        m.getSeq(),
        m.getSenderId(),
        m.getType(),
        m.isDeleted() ? null : m.getContent(),
        m.isDeleted() ? null : m.getFileId(),
        m.getSentAt(),
        m.getEditedAt(),
        m.isDeleted(),
        m.getUpdatedAt());
  }
}
