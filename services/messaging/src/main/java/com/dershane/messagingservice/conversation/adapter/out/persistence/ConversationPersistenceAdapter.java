package com.dershane.messagingservice.conversation.adapter.out.persistence;

import com.dershane.messagingservice.conversation.application.port.out.LoadConversationPort;
import com.dershane.messagingservice.conversation.application.port.out.MessageEditPort;
import com.dershane.messagingservice.conversation.application.port.out.MessageSequencePort;
import com.dershane.messagingservice.conversation.application.port.out.ReadStatePort;
import com.dershane.messagingservice.conversation.application.port.out.SaveConversationPort;
import com.dershane.messagingservice.conversation.domain.Conversation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Conversation rows are written with plain SQL: the sequence counter and the read positions must
 * change atomically in the database ({@code last_seq = last_seq + 1}), not from a loaded copy.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ConversationPersistenceAdapter
    implements LoadConversationPort,
        SaveConversationPort,
        MessageSequencePort,
        ReadStatePort,
        MessageEditPort {

  private static final String COLUMNS =
      "id, version, branch_id, student_id, teacher_id, last_seq, last_message_at";

  private final JdbcClient jdbc;
  private final Clock clock;

  @Override
  public Optional<Conversation> findById(UUID conversationId) {
    return jdbc.sql("select " + COLUMNS + " from conversation where id = :id")
        .param("id", conversationId)
        .query(ConversationPersistenceAdapter::toDomain)
        .optional();
  }

  @Override
  public Optional<Conversation> findByPair(UUID studentId, UUID teacherId) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + " from conversation where student_id = :student and teacher_id = :teacher")
        .param("student", studentId)
        .param("teacher", teacherId)
        .query(ConversationPersistenceAdapter::toDomain)
        .optional();
  }

  @Override
  public boolean insert(Conversation c) {
    Instant now = clock.instant();
    // ON CONFLICT: two simultaneous opens must not abort the transaction with a unique violation.
    int inserted =
        jdbc.sql(
                "insert into conversation (id, branch_id, student_id, teacher_id, last_seq,"
                    + " created_at, updated_at, version)"
                    + " values (:id, :branch, :student, :teacher, 0, :now, :now, 0)"
                    + " on conflict (student_id, teacher_id) do nothing")
            .param("id", c.getId())
            .param("branch", c.getBranchId())
            .param("student", c.getStudentId())
            .param("teacher", c.getTeacherId())
            .param("now", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
            .update();
    if (inserted == 0) {
      return false;
    }
    for (UUID userId : new UUID[] {c.getStudentId(), c.getTeacherId()}) {
      jdbc.sql(
              "insert into conversation_participant (conversation_id, user_id, last_read_seq)"
                  + " values (:conversation, :user, 0)")
          .param("conversation", c.getId())
          .param("user", userId)
          .update();
    }
    return true;
  }

  @Override
  public long nextSeq(UUID conversationId, Instant now) {
    return jdbc.sql(
            "update conversation set last_seq = last_seq + 1, last_message_at = :now,"
                + " updated_at = :now where id = :id returning last_seq")
        .param("now", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
        .param("id", conversationId)
        .query(Long.class)
        .single();
  }

  @Override
  public boolean advance(UUID conversationId, UUID userId, long seq) {
    return jdbc.sql(
                "update conversation_participant set last_read_seq = :seq"
                    + " where conversation_id = :conversation and user_id = :user"
                    + " and last_read_seq < :seq")
            .param("seq", seq)
            .param("conversation", conversationId)
            .param("user", userId)
            .update()
        > 0;
  }

  @Override
  public void append(UUID messageId, String previousContent, Instant editedAt) {
    jdbc.sql(
            "insert into message_edit (id, message_id, previous_content, edited_at)"
                + " values (:id, :message, :previous, :editedAt)")
        .param("id", UUID.randomUUID())
        .param("message", messageId)
        .param("previous", previousContent)
        .param("editedAt", OffsetDateTime.ofInstant(editedAt, java.time.ZoneOffset.UTC))
        .update();
  }

  private static Conversation toDomain(ResultSet rs, int row) throws SQLException {
    OffsetDateTime lastMessageAt = rs.getObject("last_message_at", OffsetDateTime.class);
    return new Conversation(
        rs.getObject("id", UUID.class),
        rs.getLong("version"),
        rs.getObject("branch_id", UUID.class),
        rs.getObject("student_id", UUID.class),
        rs.getObject("teacher_id", UUID.class),
        rs.getLong("last_seq"),
        lastMessageAt == null ? null : lastMessageAt.toInstant());
  }
}
