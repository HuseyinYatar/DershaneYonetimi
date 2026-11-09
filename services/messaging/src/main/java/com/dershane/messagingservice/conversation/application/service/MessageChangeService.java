package com.dershane.messagingservice.conversation.application.service;

import com.dershane.messagingservice.conversation.application.port.in.DeleteMessageUseCase;
import com.dershane.messagingservice.conversation.application.port.in.EditMessageUseCase;
import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.application.port.out.LoadMessagePort;
import com.dershane.messagingservice.conversation.application.port.out.MessageEditPort;
import com.dershane.messagingservice.conversation.application.port.out.RealtimeEvent;
import com.dershane.messagingservice.conversation.application.port.out.RealtimePublisherPort;
import com.dershane.messagingservice.conversation.application.port.out.SaveMessagePort;
import com.dershane.messagingservice.conversation.domain.Conversation;
import com.dershane.messagingservice.conversation.domain.Message;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.application.MessagingProperties;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edit and delete: only the sender, and nothing is ever physically removed. */
@Slf4j
@Service
@RequiredArgsConstructor
class MessageChangeService implements EditMessageUseCase, DeleteMessageUseCase {

  private final ConversationAccess access;
  private final LoadMessagePort loadMessage;
  private final SaveMessagePort saveMessage;
  private final MessageEditPort edits;
  private final RealtimePublisherPort realtime;
  private final MessagingProperties properties;
  private final Clock clock;

  @Override
  @Transactional
  @Observed(name = "message.edit")
  public MessageView edit(Actor actor, UUID conversationId, UUID messageId, String content) {
    Conversation conversation = access.requireParticipant(conversationId, actor.id());
    Message message = load(conversation, messageId);
    Instant now = clock.instant();
    String previous = message.edit(actor.id(), content, now, properties.message().editWindow());
    Message saved = saveMessage.save(message);
    edits.append(messageId, previous, now);
    MessageView view = MessageViews.of(saved);
    publish(conversation, view);
    log.atInfo().addKeyValue("messageId", messageId).log("Message edited");
    return view;
  }

  @Override
  @Transactional
  @Observed(name = "message.delete")
  public void delete(Actor actor, UUID conversationId, UUID messageId) {
    Conversation conversation = access.requireParticipant(conversationId, actor.id());
    Message message = load(conversation, messageId);
    if (!message.delete(actor.id(), clock.instant())) {
      return;
    }
    publish(conversation, MessageViews.of(saveMessage.save(message)));
    log.atInfo().addKeyValue("messageId", messageId).log("Message deleted");
  }

  private Message load(Conversation conversation, UUID messageId) {
    return loadMessage
        .findById(messageId)
        .filter(m -> m.getConversationId().equals(conversation.getId()))
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.MESSAGE_NOT_FOUND,
                    "Message does not exist in this conversation",
                    "conversationId",
                    conversation.getId(),
                    "messageId",
                    messageId));
  }

  private void publish(Conversation conversation, MessageView view) {
    realtime.publish(
        new RealtimeEvent.MessageChanged(
            Set.of(conversation.getStudentId(), conversation.getTeacherId()), view));
  }
}
