package com.dershane.messagingservice.conversation.application.service;

import com.dershane.messagingservice.conversation.application.port.in.MarkReadUseCase;
import com.dershane.messagingservice.conversation.application.port.out.ReadStatePort;
import com.dershane.messagingservice.conversation.application.port.out.RealtimeEvent;
import com.dershane.messagingservice.conversation.application.port.out.RealtimePublisherPort;
import com.dershane.messagingservice.conversation.domain.Conversation;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import io.micrometer.observation.annotation.Observed;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class MarkReadService implements MarkReadUseCase {

  private final ConversationAccess access;
  private final ReadStatePort readState;
  private final RealtimePublisherPort realtime;

  @Override
  @Transactional
  @Observed(name = "conversation.read")
  public void markRead(Actor actor, UUID conversationId, long seq) {
    Conversation conversation = access.requireParticipant(conversationId, actor.id());
    if (seq > conversation.getLastSeq()) {
      throw Rejections.reject(
          log,
          ErrorCode.READ_SEQ_OUT_OF_RANGE,
          "Read position is beyond the last message",
          "conversationId",
          conversationId,
          "seq",
          seq,
          "lastSeq",
          conversation.getLastSeq());
    }
    if (!readState.advance(conversationId, actor.id(), seq)) {
      log.atDebug()
          .addKeyValue("conversationId", conversationId)
          .addKeyValue("seq", seq)
          .log("Read position already at or beyond this message");
      return;
    }
    realtime.publish(
        new RealtimeEvent.ReadAdvanced(
            Set.of(conversation.getStudentId(), conversation.getTeacherId()),
            conversationId,
            actor.id(),
            seq));
  }
}
