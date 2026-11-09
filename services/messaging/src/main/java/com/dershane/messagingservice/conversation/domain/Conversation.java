package com.dershane.messagingservice.conversation.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A student–teacher pair. Immutable after it is opened: {@code lastSeq} and {@code lastMessageAt}
 * are advanced by {@code MessageSequencePort} with one atomic UPDATE, so the row is never written
 * from a loaded copy.
 */
@Getter
public class Conversation {

  private final UUID id;

  /** Version the conversation was loaded with; null until saved. */
  private final Long version;

  private final UUID branchId;
  private final UUID studentId;
  private final UUID teacherId;
  private final long lastSeq;
  private final Instant lastMessageAt;

  public Conversation(
      UUID id,
      Long version,
      UUID branchId,
      UUID studentId,
      UUID teacherId,
      long lastSeq,
      Instant lastMessageAt) {
    this.id = id;
    this.version = version;
    this.branchId = branchId;
    this.studentId = studentId;
    this.teacherId = teacherId;
    this.lastSeq = lastSeq;
    this.lastMessageAt = lastMessageAt;
  }

  public static Conversation open(UUID branchId, UUID studentId, UUID teacherId) {
    return new Conversation(UUID.randomUUID(), null, branchId, studentId, teacherId, 0, null);
  }

  public boolean isParticipant(UUID userId) {
    return studentId.equals(userId) || teacherId.equals(userId);
  }

  /** The other side of the conversation; the caller must be a participant. */
  public UUID otherParticipant(UUID userId) {
    return studentId.equals(userId) ? teacherId : studentId;
  }
}
