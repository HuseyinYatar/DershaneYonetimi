package com.dershane.messagingservice.conversation.adapter.out.persistence;

import com.dershane.messagingservice.conversation.application.port.in.AuditEditView;
import com.dershane.messagingservice.conversation.application.port.in.AuditMessageView;
import com.dershane.messagingservice.conversation.application.port.in.ConversationView;
import com.dershane.messagingservice.conversation.application.port.in.LastMessageView;
import com.dershane.messagingservice.conversation.application.port.in.MessageSyncCriteria;
import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.application.port.in.ParticipantView;
import com.dershane.messagingservice.conversation.application.port.in.ReportableMessage;
import com.dershane.messagingservice.conversation.application.port.out.ConversationQueryPort;
import com.dershane.messagingservice.conversation.domain.MessageType;
import com.dershane.messagingservice.directory.application.port.in.DirectoryRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Read side in plain SQL. It joins {@code user_replica} (owned by the directory module) for the
 * other participant's name; reading another module's table is allowed, writing is not.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ConversationQueryAdapter implements ConversationQueryPort {

  private static final int PREVIEW_LENGTH = 100;

  // The role comes from the conversation itself, so it is known even if the user copy is missing.
  private static final String VIEW_SQL =
      "select c.id, c.last_seq, c.last_message_at, p.last_read_seq,"
          + " (select count(*) from message m where m.conversation_id = c.id"
          + "    and m.seq > p.last_read_seq and m.sender_id <> :me and not m.deleted) as unread,"
          + " case when c.student_id = :me then c.teacher_id else c.student_id end as other_id,"
          + " case when c.student_id = :me then 'TEACHER' else 'STUDENT' end as other_role,"
          + " o.first_name, o.last_name, coalesce(o.active, false) as other_active,"
          + " lm.seq as lm_seq, lm.sender_id as lm_sender, lm.type as lm_type,"
          + " left(lm.content, "
          + PREVIEW_LENGTH
          + ") as lm_preview,"
          + " lm.deleted as lm_deleted, lm.sent_at as lm_sent_at"
          + " from conversation_participant p"
          + " join conversation c on c.id = p.conversation_id"
          + " left join user_replica o on o.user_id ="
          + "    case when c.student_id = :me then c.teacher_id else c.student_id end"
          + " left join message lm on lm.conversation_id = c.id and lm.seq = c.last_seq"
          + " where p.user_id = :me";

  private static final String MESSAGE_COLUMNS =
      "m.id, m.conversation_id, m.seq, m.sender_id, m.type, m.content, m.file_id, m.sent_at,"
          + " m.edited_at, m.deleted, m.deleted_at, m.updated_at";

  private final JdbcClient jdbc;

  @Override
  public Page<ConversationView> findViewsOf(UUID userId, Pageable pageable) {
    long total =
        jdbc.sql("select count(*) from conversation_participant where user_id = :me")
            .param("me", userId)
            .query(Long.class)
            .single();
    List<ConversationView> rows =
        jdbc.sql(
                VIEW_SQL
                    + " order by c.last_message_at desc nulls last, c.id"
                    + " limit :limit offset :offset")
            .param("me", userId)
            .param("limit", pageable.getPageSize())
            .param("offset", pageable.getOffset())
            .query(ConversationQueryAdapter::toConversationView)
            .list();
    return new PageImpl<>(rows, pageable, total);
  }

  @Override
  public Optional<ConversationView> findViewOf(UUID conversationId, UUID userId) {
    return jdbc.sql(VIEW_SQL + " and c.id = :id")
        .param("me", userId)
        .param("id", conversationId)
        .query(ConversationQueryAdapter::toConversationView)
        .optional();
  }

  @Override
  public MessageRows findMessageRows(UUID conversationId, MessageSyncCriteria criteria) {
    int limit = criteria.limit();
    if (criteria.afterSeq() == null) {
      return latestBefore(conversationId, criteria.beforeSeq(), limit);
    }
    List<MessageView> newer =
        jdbc.sql(
                "select "
                    + MESSAGE_COLUMNS
                    + " from message m where m.conversation_id = :c and m.seq > :after"
                    + " order by m.seq limit :n")
            .param("c", conversationId)
            .param("after", criteria.afterSeq())
            .param("n", limit + 1)
            .query(ConversationQueryAdapter::toMessageView)
            .list();
    boolean hasMore = newer.size() > limit;
    List<MessageView> result = new ArrayList<>();
    if (criteria.updatedSince() != null) {
      // shortcut: unbounded, but only messages edited or deleted since the last sync; cap it if
      // a conversation ever sees thousands of edits between two syncs.
      result.addAll(
          jdbc.sql(
                  "select "
                      + MESSAGE_COLUMNS
                      + " from message m where m.conversation_id = :c and m.seq <= :after"
                      + " and m.updated_at > :since order by m.seq")
              .param("c", conversationId)
              .param("after", criteria.afterSeq())
              .param(
                  "since",
                  OffsetDateTime.ofInstant(criteria.updatedSince(), java.time.ZoneOffset.UTC))
              .query(ConversationQueryAdapter::toMessageView)
              .list());
    }
    result.addAll(hasMore ? newer.subList(0, limit) : newer);
    return new MessageRows(result, hasMore);
  }

  private MessageRows latestBefore(UUID conversationId, Long beforeSeq, int limit) {
    List<MessageView> rows =
        jdbc.sql(
                "select "
                    + MESSAGE_COLUMNS
                    + " from message m where m.conversation_id = :c"
                    + (beforeSeq == null ? "" : " and m.seq < :before")
                    + " order by m.seq desc limit :n")
            .param("c", conversationId)
            .params(beforeSeq == null ? Map.of() : Map.of("before", beforeSeq))
            .param("n", limit + 1)
            .query(ConversationQueryAdapter::toMessageView)
            .list();
    boolean hasMore = rows.size() > limit;
    List<MessageView> page = new ArrayList<>(hasMore ? rows.subList(0, limit) : rows);
    Collections.reverse(page);
    return new MessageRows(page, hasMore);
  }

  @Override
  public Optional<ReportableMessage> findReportable(UUID messageId) {
    return jdbc.sql(
            "select m.id, m.conversation_id, c.branch_id, m.sender_id"
                + " from message m join conversation c on c.id = m.conversation_id"
                + " where m.id = :id")
        .param("id", messageId)
        .query(
            (rs, row) ->
                new ReportableMessage(
                    rs.getObject("id", UUID.class),
                    rs.getObject("conversation_id", UUID.class),
                    rs.getObject("branch_id", UUID.class),
                    rs.getObject("sender_id", UUID.class)))
        .optional();
  }

  @Override
  public List<AuditMessageView> findAuditMessages(UUID conversationId, Long beforeSeq, int limit) {
    List<MessageAudit> rows =
        jdbc.sql(
                "select "
                    + MESSAGE_COLUMNS
                    + " from message m where m.conversation_id = :c"
                    + (beforeSeq == null ? "" : " and m.seq < :before")
                    + " order by m.seq desc limit :n")
            .param("c", conversationId)
            .params(beforeSeq == null ? Map.of() : Map.of("before", beforeSeq))
            .param("n", limit)
            .query(ConversationQueryAdapter::toAudit)
            .list();
    List<AuditMessageView> result = withEdits(rows);
    Collections.reverse(result);
    return result;
  }

  @Override
  public Optional<AuditMessageView> findAuditMessage(UUID messageId) {
    List<MessageAudit> rows =
        jdbc.sql("select " + MESSAGE_COLUMNS + " from message m where m.id = :id")
            .param("id", messageId)
            .query(ConversationQueryAdapter::toAudit)
            .list();
    return withEdits(rows).stream().findFirst();
  }

  private List<AuditMessageView> withEdits(List<MessageAudit> rows) {
    if (rows.isEmpty()) {
      return new ArrayList<>();
    }
    Map<UUID, List<AuditEditView>> edits = new LinkedHashMap<>();
    jdbc.sql(
            "select message_id, previous_content, edited_at from message_edit"
                + " where message_id in (:ids) order by edited_at")
        .param("ids", rows.stream().map(MessageAudit::id).toList())
        .query(
            (rs, row) -> {
              edits
                  .computeIfAbsent(rs.getObject("message_id", UUID.class), k -> new ArrayList<>())
                  .add(
                      new AuditEditView(
                          rs.getString("previous_content"),
                          rs.getObject("edited_at", OffsetDateTime.class).toInstant()));
              return null;
            })
        .list();
    List<AuditMessageView> result = new ArrayList<>();
    for (MessageAudit r : rows) {
      result.add(
          new AuditMessageView(
              r.id(),
              r.seq(),
              r.senderId(),
              r.type(),
              r.content(),
              r.fileId(),
              r.sentAt(),
              r.editedAt(),
              r.deleted(),
              r.deletedAt(),
              edits.getOrDefault(r.id(), List.of())));
    }
    return result;
  }

  private record MessageAudit(
      UUID id,
      long seq,
      UUID senderId,
      MessageType type,
      String content,
      UUID fileId,
      Instant sentAt,
      Instant editedAt,
      boolean deleted,
      Instant deletedAt) {}

  private static MessageAudit toAudit(ResultSet rs, int row) throws SQLException {
    return new MessageAudit(
        rs.getObject("id", UUID.class),
        rs.getLong("seq"),
        rs.getObject("sender_id", UUID.class),
        MessageType.valueOf(rs.getString("type")),
        rs.getString("content"),
        rs.getObject("file_id", UUID.class),
        instant(rs, "sent_at"),
        instant(rs, "edited_at"),
        rs.getBoolean("deleted"),
        instant(rs, "deleted_at"));
  }

  private static MessageView toMessageView(ResultSet rs, int row) throws SQLException {
    boolean deleted = rs.getBoolean("deleted");
    return new MessageView(
        rs.getObject("id", UUID.class),
        rs.getObject("conversation_id", UUID.class),
        rs.getLong("seq"),
        rs.getObject("sender_id", UUID.class),
        MessageType.valueOf(rs.getString("type")),
        deleted ? null : rs.getString("content"),
        deleted ? null : rs.getObject("file_id", UUID.class),
        instant(rs, "sent_at"),
        instant(rs, "edited_at"),
        deleted,
        instant(rs, "updated_at"));
  }

  private static ConversationView toConversationView(ResultSet rs, int row) throws SQLException {
    ParticipantView other =
        new ParticipantView(
            rs.getObject("other_id", UUID.class),
            rs.getString("first_name"),
            rs.getString("last_name"),
            DirectoryRole.valueOf(rs.getString("other_role")),
            rs.getBoolean("other_active"));
    long lastMessageSeq = rs.getLong("lm_seq");
    LastMessageView last = null;
    if (!rs.wasNull()) {
      boolean deleted = rs.getBoolean("lm_deleted");
      last =
          new LastMessageView(
              lastMessageSeq,
              rs.getObject("lm_sender", UUID.class),
              MessageType.valueOf(rs.getString("lm_type")),
              deleted ? null : rs.getString("lm_preview"),
              deleted,
              instant(rs, "lm_sent_at"));
    }
    return new ConversationView(
        rs.getObject("id", UUID.class),
        other,
        rs.getLong("last_seq"),
        rs.getLong("last_read_seq"),
        rs.getLong("unread"),
        instant(rs, "last_message_at"),
        last);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
