package com.dershane.messagingservice.conversation.domain;

import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A message. Never physically deleted: {@link #delete} only sets a flag, so the content stays
 * available to an administrator reviewing a report. Edits keep the previous text in {@code
 * message_edit}.
 */
@Slf4j
@Getter
public class Message {

  private final UUID id;

  /** Version the message was loaded with; null until saved. */
  private final Long version;

  private final UUID conversationId;
  private final long seq;
  private final UUID senderId;
  private final MessageType type;
  private final UUID fileId;
  private final Instant sentAt;

  /** Set by the persistence layer on every change; null until saved. */
  private final Instant updatedAt;

  // Free text written by a child or a teacher: never logged, masked in toString.
  private String content;

  private Instant editedAt;
  private boolean deleted;
  private Instant deletedAt;

  @SuppressWarnings("checkstyle:ParameterNumber")
  public Message(
      UUID id,
      Long version,
      UUID conversationId,
      long seq,
      UUID senderId,
      MessageType type,
      String content,
      UUID fileId,
      Instant sentAt,
      Instant editedAt,
      boolean deleted,
      Instant deletedAt,
      Instant updatedAt) {
    this.id = id;
    this.version = version;
    this.conversationId = conversationId;
    this.seq = seq;
    this.senderId = senderId;
    this.type = type;
    this.content = content;
    this.fileId = fileId;
    this.sentAt = sentAt;
    this.editedAt = editedAt;
    this.deleted = deleted;
    this.deletedAt = deletedAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Builds a new message. A TEXT message needs text and no photo; a PHOTO message needs a photo and
   * may have a caption.
   */
  public static Message create(
      UUID conversationId,
      long seq,
      UUID senderId,
      MessageType type,
      String content,
      UUID fileId,
      Instant now) {
    String body = content;
    if (type == MessageType.TEXT) {
      if (fileId != null) {
        throw Rejections.reject(
            log,
            ErrorCode.INVALID_MESSAGE_BODY,
            "A text message cannot carry a file",
            "conversationId",
            conversationId,
            "senderId",
            senderId);
      }
      requireText(body, conversationId, senderId);
    } else {
      if (fileId == null) {
        throw Rejections.reject(
            log,
            ErrorCode.PHOTO_REQUIRED,
            "A photo message needs a file",
            "conversationId",
            conversationId,
            "senderId",
            senderId);
      }
      body = body == null || body.isBlank() ? null : body;
    }
    return new Message(
        UUID.randomUUID(),
        null,
        conversationId,
        seq,
        senderId,
        type,
        body,
        fileId,
        now,
        null,
        false,
        null,
        null);
  }

  /**
   * Replaces the text (the caption, for a photo).
   *
   * @return the previous text, for the audit trail
   */
  public String edit(UUID actorId, String newContent, Instant now, Duration editWindow) {
    requireSender(actorId, "edit");
    if (deleted) {
      throw Rejections.reject(
          log, ErrorCode.MESSAGE_DELETED, "Deleted message cannot be edited", "messageId", id);
    }
    if (now.isAfter(sentAt.plus(editWindow))) {
      throw Rejections.reject(
          log,
          ErrorCode.EDIT_WINDOW_EXPIRED,
          "The edit window has passed",
          "messageId",
          id,
          "sentAt",
          sentAt);
    }
    String previous = content;
    if (type == MessageType.TEXT) {
      requireText(newContent, conversationId, actorId);
      content = newContent;
    } else {
      content = newContent == null || newContent.isBlank() ? null : newContent;
    }
    editedAt = now;
    return previous;
  }

  /**
   * Marks the message deleted.
   *
   * @return false when it already was (a repeated request; nothing changed)
   */
  public boolean delete(UUID actorId, Instant now) {
    requireSender(actorId, "delete");
    if (deleted) {
      log.atDebug().addKeyValue("messageId", id).log("Message already deleted");
      return false;
    }
    deleted = true;
    deletedAt = now;
    return true;
  }

  private void requireSender(UUID actorId, String action) {
    if (!senderId.equals(actorId)) {
      throw Rejections.reject(
          log,
          ErrorCode.NOT_MESSAGE_SENDER,
          "Only the sender can change a message",
          "messageId",
          id,
          "callerId",
          actorId,
          "action",
          action);
    }
  }

  private static void requireText(String text, UUID conversationId, UUID senderId) {
    if (text == null || text.isBlank()) {
      throw Rejections.reject(
          log,
          ErrorCode.EMPTY_MESSAGE,
          "A text message cannot be empty",
          "conversationId",
          conversationId,
          "senderId",
          senderId);
    }
  }

  @Override
  public String toString() {
    return "Message[id=" + id + ", conversationId=" + conversationId + ", seq=" + seq + "]";
  }
}
