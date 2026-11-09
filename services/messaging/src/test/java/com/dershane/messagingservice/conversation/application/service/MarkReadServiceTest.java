package com.dershane.messagingservice.conversation.application.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.messagingservice.conversation.application.port.out.ReadStatePort;
import com.dershane.messagingservice.conversation.application.port.out.RealtimeEvent;
import com.dershane.messagingservice.conversation.application.port.out.RealtimePublisherPort;
import com.dershane.messagingservice.conversation.domain.Conversation;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.domain.DomainException;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkReadServiceTest {

  @Mock private ConversationAccess access;
  @Mock private ReadStatePort readState;
  @Mock private RealtimePublisherPort realtime;

  private final UUID studentId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final Conversation conversation =
      new Conversation(UUID.randomUUID(), 0L, UUID.randomUUID(), studentId, teacherId, 10, null);
  private final Actor student = new Actor(studentId, Set.of(Actor.STUDENT), null);

  private MarkReadService service() {
    when(access.requireParticipant(conversation.getId(), studentId)).thenReturn(conversation);
    return new MarkReadService(access, readState, realtime);
  }

  @Test
  void aPositionBeyondTheLastMessageIsRefused() {
    MarkReadService service = service();

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.markRead(student, conversation.getId(), 11))
        .extracting(DomainException::getErrorCode)
        .isEqualTo(ErrorCode.READ_SEQ_OUT_OF_RANGE);
    verify(readState, never()).advance(any(), any(), org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void aMovedPositionIsAnnouncedToBothSides() {
    MarkReadService service = service();
    when(readState.advance(conversation.getId(), studentId, 7)).thenReturn(true);

    service.markRead(student, conversation.getId(), 7);

    verify(realtime)
        .publish(
            new RealtimeEvent.ReadAdvanced(
                Set.of(studentId, teacherId), conversation.getId(), studentId, 7));
  }

  @Test
  void anUnchangedPositionIsSilent() {
    MarkReadService service = service();
    when(readState.advance(conversation.getId(), studentId, 3)).thenReturn(false);

    service.markRead(student, conversation.getId(), 3);

    verify(realtime, never()).publish(any());
  }
}
