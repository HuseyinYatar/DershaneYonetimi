package com.dershane.messagingservice.conversation.application.service;

import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.application.port.in.SendMessageCommand;
import com.dershane.messagingservice.conversation.application.port.in.SendMessageUseCase;
import com.dershane.messagingservice.conversation.application.port.out.MessageSequencePort;
import com.dershane.messagingservice.conversation.application.port.out.PresencePort;
import com.dershane.messagingservice.conversation.application.port.out.ReadStatePort;
import com.dershane.messagingservice.conversation.application.port.out.RealtimeEvent;
import com.dershane.messagingservice.conversation.application.port.out.RealtimePublisherPort;
import com.dershane.messagingservice.conversation.application.port.out.SaveMessagePort;
import com.dershane.messagingservice.conversation.domain.Conversation;
import com.dershane.messagingservice.conversation.domain.Message;
import com.dershane.messagingservice.directory.application.port.in.UserDirectoryQuery;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.application.MetricNames;
import com.dershane.messagingservice.shared.application.event.IntegrationEvent;
import com.dershane.messagingservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class SendMessageService implements SendMessageUseCase {

  private final ConversationAccess access;
  private final UserDirectoryQuery directory;
  private final PresencePort presence;
  private final MessageSequencePort sequence;
  private final SaveMessagePort saveMessage;
  private final ReadStatePort readState;
  private final IntegrationEventPublisher events;
  private final RealtimePublisherPort realtime;
  private final MeterRegistry meters;
  private final Clock clock;

  @Override
  @Transactional
  @Observed(name = "message.send")
  public MessageView send(Actor actor, UUID conversationId, SendMessageCommand command) {
    Conversation conversation = access.requireParticipant(conversationId, actor.id());
    UUID recipientId = conversation.otherParticipant(actor.id());
    requireActiveRecipient(conversationId, recipientId);

    // Read before the row lock below: a slow Redis must not lengthen the lock.
    boolean recipientOnline = presence.isOnline(recipientId);
    Instant now = clock.instant();

    // The UPDATE takes the conversation row lock until commit, so numbers follow commit order.
    long seq = sequence.nextSeq(conversationId, now);
    Message message =
        saveMessage.save(
            Message.create(
                conversationId,
                seq,
                actor.id(),
                command.type(),
                command.content(),
                command.fileId(),
                now));
    readState.advance(conversationId, actor.id(), seq);

    if (recipientOnline) {
      log.atDebug()
          .addKeyValue("messageId", message.getId())
          .addKeyValue("reason", "online")
          .log("new_message.notification_skipped");
    } else {
      events.publish(
          new IntegrationEvent.NewMessage(
              conversationId, message.getId(), actor.id(), recipientId, message.getSentAt()));
    }

    MessageView view = MessageViews.of(message);
    realtime.publish(
        new RealtimeEvent.MessageChanged(
            Set.of(conversation.getStudentId(), conversation.getTeacherId()), view));
    meters.counter(MetricNames.MESSAGES_SENT, "type", command.type().name()).increment();
    log.atInfo()
        .addKeyValue("conversationId", conversationId)
        .addKeyValue("messageId", message.getId())
        .addKeyValue("seq", seq)
        .log("Message sent");
    return view;
  }

  /** A conversation with a deactivated user stays readable but accepts no new messages. */
  private void requireActiveRecipient(UUID conversationId, UUID recipientId) {
    directory
        .find(recipientId)
        .filter(entry -> !entry.active())
        .ifPresent(
            entry -> {
              throw Rejections.reject(
                  log,
                  ErrorCode.PARTICIPANT_INACTIVE,
                  "The recipient is inactive",
                  "conversationId",
                  conversationId,
                  "recipientId",
                  recipientId);
            });
  }
}
