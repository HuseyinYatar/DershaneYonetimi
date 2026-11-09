package com.dershane.messagingservice.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.messagingservice.shared.domain.DomainException;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageTest {

  private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");
  private static final Duration WINDOW = Duration.ofMinutes(15);

  private final UUID conversation = UUID.randomUUID();
  private final UUID sender = UUID.randomUUID();

  private Message text(String content) {
    return Message.create(conversation, 1, sender, MessageType.TEXT, content, null, NOW);
  }

  private static void assertRejected(Runnable action, ErrorCode code) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .extracting(DomainException::getErrorCode)
        .isEqualTo(code);
  }

  @Test
  void aTextMessageNeedsText() {
    assertThat(text("hello").getContent()).isEqualTo("hello");
    assertRejected(() -> text("  "), ErrorCode.EMPTY_MESSAGE);
    assertRejected(() -> text(null), ErrorCode.EMPTY_MESSAGE);
  }

  @Test
  void aTextMessageCannotCarryAFile() {
    assertRejected(
        () ->
            Message.create(conversation, 1, sender, MessageType.TEXT, "hi", UUID.randomUUID(), NOW),
        ErrorCode.INVALID_MESSAGE_BODY);
  }

  @Test
  void aPhotoMessageNeedsAFileAndMayHaveACaption() {
    assertRejected(
        () -> Message.create(conversation, 1, sender, MessageType.PHOTO, "q", null, NOW),
        ErrorCode.PHOTO_REQUIRED);

    Message photo =
        Message.create(conversation, 1, sender, MessageType.PHOTO, " ", UUID.randomUUID(), NOW);
    assertThat(photo.getContent()).as("a blank caption is dropped").isNull();
  }

  @Test
  void theSenderEditsWithinTheWindowAndGetsTheOldText() {
    Message message = text("first");

    String previous = message.edit(sender, "second", NOW.plusSeconds(60), WINDOW);

    assertThat(previous).isEqualTo("first");
    assertThat(message.getContent()).isEqualTo("second");
    assertThat(message.getEditedAt()).isEqualTo(NOW.plusSeconds(60));
  }

  @Test
  void editingIsRefusedForOthersAfterTheWindowAndOnceDeleted() {
    Message message = text("first");

    assertRejected(
        () -> message.edit(UUID.randomUUID(), "x", NOW, WINDOW), ErrorCode.NOT_MESSAGE_SENDER);
    assertRejected(
        () -> message.edit(sender, "x", NOW.plus(WINDOW).plusSeconds(1), WINDOW),
        ErrorCode.EDIT_WINDOW_EXPIRED);
    assertRejected(() -> message.edit(sender, " ", NOW, WINDOW), ErrorCode.EMPTY_MESSAGE);

    message.delete(sender, NOW);
    assertRejected(() -> message.edit(sender, "x", NOW, WINDOW), ErrorCode.MESSAGE_DELETED);
  }

  @Test
  void deleteIsSenderOnlyAndRepeatedDeleteChangesNothing() {
    Message message = text("first");

    assertRejected(() -> message.delete(UUID.randomUUID(), NOW), ErrorCode.NOT_MESSAGE_SENDER);
    assertThat(message.delete(sender, NOW)).isTrue();
    assertThat(message.delete(sender, NOW.plusSeconds(5))).isFalse();
    assertThat(message.isDeleted()).isTrue();
    assertThat(message.getDeletedAt()).isEqualTo(NOW);
    assertThat(message.getContent()).as("the content stays for audit").isEqualTo("first");
  }

  @Test
  void toStringHidesTheText() {
    assertThat(text("secret words").toString()).doesNotContain("secret");
  }
}
