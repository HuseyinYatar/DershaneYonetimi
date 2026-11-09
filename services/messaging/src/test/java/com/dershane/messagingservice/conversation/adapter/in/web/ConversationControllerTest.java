package com.dershane.messagingservice.conversation.adapter.in.web;

import static com.dershane.messagingservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.messagingservice.conversation.application.port.in.ConversationQuery;
import com.dershane.messagingservice.conversation.application.port.in.ConversationView;
import com.dershane.messagingservice.conversation.application.port.in.DeleteMessageUseCase;
import com.dershane.messagingservice.conversation.application.port.in.EditMessageUseCase;
import com.dershane.messagingservice.conversation.application.port.in.MarkReadUseCase;
import com.dershane.messagingservice.conversation.application.port.in.MessagePage;
import com.dershane.messagingservice.conversation.application.port.in.MessageQuery;
import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.application.port.in.OpenConversationUseCase;
import com.dershane.messagingservice.conversation.application.port.in.OpenedConversation;
import com.dershane.messagingservice.conversation.application.port.in.ParticipantView;
import com.dershane.messagingservice.conversation.application.port.in.SendMessageUseCase;
import com.dershane.messagingservice.conversation.domain.MessageType;
import com.dershane.messagingservice.directory.application.port.in.DirectoryRole;
import com.dershane.messagingservice.shared.domain.DomainException;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.support.WebMvcTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConversationController.class)
@Import({WebMvcTestSupport.class, ConversationWebMapperImpl.class})
class ConversationControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private OpenConversationUseCase openConversation;
  @MockitoBean private ConversationQuery conversations;
  @MockitoBean private SendMessageUseCase sendMessage;
  @MockitoBean private EditMessageUseCase editMessage;
  @MockitoBean private DeleteMessageUseCase deleteMessage;
  @MockitoBean private MarkReadUseCase markRead;
  @MockitoBean private MessageQuery messages;

  private final UUID branchId = UUID.randomUUID();
  private final UUID conversationId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();

  private ConversationView view() {
    return new ConversationView(
        conversationId,
        new ParticipantView(teacherId, "Ada", "Yilmaz", DirectoryRole.TEACHER, true),
        0,
        0,
        0,
        null,
        null);
  }

  private MessageView messageView() {
    return new MessageView(
        UUID.randomUUID(),
        conversationId,
        1,
        UUID.randomUUID(),
        MessageType.TEXT,
        "hello",
        null,
        Instant.parse("2026-11-09T08:00:00Z"),
        null,
        false,
        Instant.parse("2026-11-09T08:00:00Z"));
  }

  @Test
  void guardiansAndAdminsCannotUseConversations() throws Exception {
    for (String role : new String[] {"guardian", "admin"}) {
      mvc.perform(get("/api/v1/conversations").with(token(UUID.randomUUID(), branchId, role)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
    verifyNoInteractions(conversations);
  }

  @Test
  void openingANewConversationReturnsCreatedWithItsLocation() throws Exception {
    when(openConversation.open(any(), any())).thenReturn(new OpenedConversation(view(), true));

    mvc.perform(
            post("/api/v1/conversations")
                .with(token(UUID.randomUUID(), branchId, "student"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"participantId\":\"" + teacherId + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/conversations/" + conversationId))
        .andExpect(jsonPath("$.otherParticipant.role").value("TEACHER"));
  }

  @Test
  void openingAnExistingConversationReturnsOk() throws Exception {
    when(openConversation.open(any(), any())).thenReturn(new OpenedConversation(view(), false));

    mvc.perform(
            post("/api/v1/conversations")
                .with(token(UUID.randomUUID(), branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"participantId\":\"" + teacherId + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void aRuleViolationIsATurkishProblemDetail() throws Exception {
    when(openConversation.open(any(), any()))
        .thenThrow(new DomainException(ErrorCode.CONVERSATION_NOT_ALLOWED));

    mvc.perform(
            post("/api/v1/conversations")
                .with(token(UUID.randomUUID(), branchId, "student"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"participantId\":\"" + teacherId + "\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("CONVERSATION_NOT_ALLOWED"))
        .andExpect(
            jsonPath("$.detail")
                .value("Yalnızca kendi şubenizdeki öğrenci veya öğretmenle yazışabilirsiniz."));
  }

  @Test
  void sendingAMessageReturnsItWithItsLocation() throws Exception {
    MessageView sent = messageView();
    when(sendMessage.send(any(), any(), any())).thenReturn(sent);

    mvc.perform(
            post("/api/v1/conversations/" + conversationId + "/messages")
                .with(token(UUID.randomUUID(), branchId, "student"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"TEXT\",\"content\":\"hello\"}"))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    "/api/v1/conversations/" + conversationId + "/messages/" + sent.id()))
        .andExpect(jsonPath("$.seq").value(1))
        .andExpect(jsonPath("$.content").value("hello"));
  }

  @Test
  void aMissingTypeAndAnOversizedTextAreBadRequests() throws Exception {
    mvc.perform(
            post("/api/v1/conversations/" + conversationId + "/messages")
                .with(token(UUID.randomUUID(), branchId, "student"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    mvc.perform(
            post("/api/v1/conversations/" + conversationId + "/messages")
                .with(token(UUID.randomUUID(), branchId, "student"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"TEXT\",\"content\":\"" + "a".repeat(4001) + "\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(sendMessage);
  }

  @Test
  void theSyncQueryReturnsMessagesAndTheSyncMark() throws Exception {
    when(messages.list(any(), any(), any()))
        .thenReturn(
            new MessagePage(List.of(messageView()), false, Instant.parse("2026-11-09T07:59:50Z")));

    mvc.perform(
            get("/api/v1/conversations/" + conversationId + "/messages")
                .param("afterSeq", "0")
                .with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.messages[0].seq").value(1))
        .andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.syncedAt").value("2026-11-09T07:59:50Z"));
  }

  @Test
  void aLimitOutsideTheRangeIsABadRequest() throws Exception {
    mvc.perform(
            get("/api/v1/conversations/" + conversationId + "/messages")
                .param("limit", "500")
                .with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(messages);
  }
}
