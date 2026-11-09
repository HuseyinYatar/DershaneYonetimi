package com.dershane.messagingservice.conversation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.application.port.in.SendMessageCommand;
import com.dershane.messagingservice.conversation.application.port.out.MessageSequencePort;
import com.dershane.messagingservice.conversation.application.port.out.PresencePort;
import com.dershane.messagingservice.conversation.application.port.out.ReadStatePort;
import com.dershane.messagingservice.conversation.application.port.out.RealtimeEvent;
import com.dershane.messagingservice.conversation.application.port.out.RealtimePublisherPort;
import com.dershane.messagingservice.conversation.application.port.out.SaveMessagePort;
import com.dershane.messagingservice.conversation.domain.Conversation;
import com.dershane.messagingservice.conversation.domain.Message;
import com.dershane.messagingservice.conversation.domain.MessageType;
import com.dershane.messagingservice.directory.application.port.in.DirectoryEntryView;
import com.dershane.messagingservice.directory.application.port.in.DirectoryRole;
import com.dershane.messagingservice.directory.application.port.in.UserDirectoryQuery;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.application.event.IntegrationEvent;
import com.dershane.messagingservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.messagingservice.shared.domain.DomainException;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendMessageServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");

  @Mock private ConversationAccess access;
  @Mock private UserDirectoryQuery directory;
  @Mock private PresencePort presence;
  @Mock private MessageSequencePort sequence;
  @Mock private SaveMessagePort saveMessage;
  @Mock private ReadStatePort readState;
  @Mock private IntegrationEventPublisher events;
  @Mock private RealtimePublisherPort realtime;

  private final UUID branchId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final Conversation conversation =
      new Conversation(UUID.randomUUID(), 0L, branchId, studentId, teacherId, 4, null);
  private final Actor student = new Actor(studentId, Set.of(Actor.STUDENT), branchId);
  private SendMessageService service;

  @BeforeEach
  void setUp() {
    service =
        new SendMessageService(
            access,
            directory,
            presence,
            sequence,
            saveMessage,
            readState,
            events,
            realtime,
            new SimpleMeterRegistry(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(access.requireParticipant(conversation.getId(), studentId)).thenReturn(conversation);
    when(directory.find(teacherId)).thenReturn(Optional.of(entry(true)));
    lenient().when(sequence.nextSeq(conversation.getId(), NOW)).thenReturn(5L);
    lenient().when(saveMessage.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));
  }

  private DirectoryEntryView entry(boolean active) {
    return new DirectoryEntryView(teacherId, DirectoryRole.TEACHER, branchId, "T", "T", active);
  }

  private MessageView send() {
    return service.send(
        student, conversation.getId(), new SendMessageCommand(MessageType.TEXT, "hello", null));
  }

  @Test
  void theMessageGetsTheNextNumberAndTheSendersReadPositionFollowsIt() {
    when(presence.isOnline(teacherId)).thenReturn(true);

    MessageView sent = send();

    assertThat(sent.seq()).isEqualTo(5);
    assertThat(sent.senderId()).isEqualTo(studentId);
    verify(readState).advance(conversation.getId(), studentId, 5);
  }

  @Test
  void anOfflineRecipientGetsOneNewMessageEventPerMessage() {
    when(presence.isOnline(teacherId)).thenReturn(false);

    MessageView sent = send();

    ArgumentCaptor<IntegrationEvent> event = ArgumentCaptor.forClass(IntegrationEvent.class);
    verify(events).publish(event.capture());
    IntegrationEvent.NewMessage newMessage = (IntegrationEvent.NewMessage) event.getValue();
    assertThat(newMessage.recipientId()).isEqualTo(teacherId);
    assertThat(newMessage.senderId()).isEqualTo(studentId);
    assertThat(newMessage.messageId()).isEqualTo(sent.id());
  }

  @Test
  void anOnlineRecipientGetsNoEvent() {
    when(presence.isOnline(teacherId)).thenReturn(true);

    send();

    verify(events, never()).publish(any());
  }

  @Test
  void bothParticipantsAreToldAboutTheMessage() {
    when(presence.isOnline(teacherId)).thenReturn(true);

    MessageView sent = send();

    ArgumentCaptor<RealtimeEvent> event = ArgumentCaptor.forClass(RealtimeEvent.class);
    verify(realtime).publish(event.capture());
    assertThat(event.getValue().recipients()).containsExactlyInAnyOrder(studentId, teacherId);
    assertThat(((RealtimeEvent.MessageChanged) event.getValue()).message()).isEqualTo(sent);
  }

  @Test
  void aConversationWithAnInactiveUserIsReadOnly() {
    when(directory.find(teacherId)).thenReturn(Optional.of(entry(false)));

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(this::send)
        .extracting(DomainException::getErrorCode)
        .isEqualTo(ErrorCode.PARTICIPANT_INACTIVE);
    verify(sequence, never()).nextSeq(eq(conversation.getId()), any());
  }

  @Test
  void anEmptyTextWritesNothing() {
    when(presence.isOnline(teacherId)).thenReturn(true);

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                service.send(
                    student,
                    conversation.getId(),
                    new SendMessageCommand(MessageType.TEXT, " ", null)))
        .extracting(DomainException::getErrorCode)
        .isEqualTo(ErrorCode.EMPTY_MESSAGE);
    verify(saveMessage, never()).save(any());
    verify(realtime, never()).publish(any());
  }
}
