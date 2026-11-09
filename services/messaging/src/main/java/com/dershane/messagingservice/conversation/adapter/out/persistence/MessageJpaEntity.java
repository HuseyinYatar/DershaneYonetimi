package com.dershane.messagingservice.conversation.adapter.out.persistence;

import com.dershane.messagingservice.conversation.domain.MessageType;
import com.dershane.messagingservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class MessageJpaEntity extends AuditableJpaEntity {

  @Id @ToString.Include private UUID id;

  @Column(name = "conversation_id", nullable = false, updatable = false)
  private UUID conversationId;

  @Column(name = "seq", nullable = false, updatable = false)
  private long seq;

  @Column(name = "sender_id", nullable = false, updatable = false)
  private UUID senderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false)
  private MessageType type;

  @Column(name = "content")
  private String content;

  @Column(name = "file_id", updatable = false)
  private UUID fileId;

  @Column(name = "sent_at", nullable = false, updatable = false)
  private Instant sentAt;

  @Column(name = "edited_at")
  private Instant editedAt;

  @Column(name = "deleted", nullable = false)
  private boolean deleted;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
