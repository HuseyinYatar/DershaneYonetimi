package com.dershane.messagingservice.conversation.adapter.out.persistence;

import com.dershane.messagingservice.conversation.application.port.out.LoadMessagePort;
import com.dershane.messagingservice.conversation.application.port.out.SaveMessagePort;
import com.dershane.messagingservice.conversation.domain.Message;
import com.dershane.messagingservice.shared.adapter.out.persistence.VersionChecks;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class MessagePersistenceAdapter implements LoadMessagePort, SaveMessagePort {

  private final MessageJpaRepository messages;

  @Override
  public Optional<Message> findById(UUID messageId) {
    return messages.findById(messageId).map(MessagePersistenceAdapter::toDomain);
  }

  @Override
  public Message save(Message message) {
    MessageJpaEntity entity;
    if (message.getVersion() == null) {
      entity = new MessageJpaEntity();
      entity.setId(message.getId());
      entity.setConversationId(message.getConversationId());
      entity.setSeq(message.getSeq());
      entity.setSenderId(message.getSenderId());
      entity.setType(message.getType());
      entity.setFileId(message.getFileId());
      entity.setSentAt(message.getSentAt());
    } else {
      entity = messages.findById(message.getId()).orElseThrow();
      VersionChecks.requireCurrent(
          log, "Message", message.getId(), message.getVersion(), entity.getVersion());
    }
    entity.setContent(message.getContent());
    entity.setEditedAt(message.getEditedAt());
    entity.setDeleted(message.isDeleted());
    entity.setDeletedAt(message.getDeletedAt());
    // Flushed so the auditing fields (updatedAt, version) are filled before they are read back.
    return toDomain(messages.saveAndFlush(entity));
  }

  private static Message toDomain(MessageJpaEntity e) {
    return new Message(
        e.getId(),
        e.getVersion(),
        e.getConversationId(),
        e.getSeq(),
        e.getSenderId(),
        e.getType(),
        e.getContent(),
        e.getFileId(),
        e.getSentAt(),
        e.getEditedAt(),
        e.isDeleted(),
        e.getDeletedAt(),
        e.getUpdatedAt());
  }
}
