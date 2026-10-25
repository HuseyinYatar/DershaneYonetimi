package com.dershane.userservice.shared.adapter.out.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** A row of the transactional outbox (diagram: GidenOlay). */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class OutboxEvent implements Persistable<UUID> {

  private static final int MAX_ERROR_LENGTH = 500;

  @Id
  @Column(name = "event_id")
  @ToString.Include
  private UUID eventId;

  @Column(name = "event_type", nullable = false, length = 64, updatable = false)
  @ToString.Include
  private String eventType;

  @Column(name = "aggregate_id", nullable = false, updatable = false)
  private UUID aggregateId;

  /** Serialized {@link EventEnvelope}. Contains personal data: never log it. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, updatable = false)
  private String payload;

  @Column(name = "traceparent", length = 64, updatable = false)
  private String traceparent;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error", length = MAX_ERROR_LENGTH)
  private String lastError;

  @Transient private boolean isNew;

  public static OutboxEvent pending(
      UUID eventId,
      String eventType,
      UUID aggregateId,
      String payload,
      String traceparent,
      Instant createdAt) {
    OutboxEvent event = new OutboxEvent();
    event.eventId = eventId;
    event.eventType = eventType;
    event.aggregateId = aggregateId;
    event.payload = payload;
    event.traceparent = traceparent;
    event.createdAt = createdAt;
    event.isNew = true;
    return event;
  }

  public void markPublished(Instant at) {
    this.publishedAt = at;
    this.attemptCount++;
    this.lastError = null;
  }

  public void markFailed(String error) {
    this.attemptCount++;
    this.lastError =
        error == null || error.length() <= MAX_ERROR_LENGTH
            ? error
            : error.substring(0, MAX_ERROR_LENGTH);
  }

  @Override
  public UUID getId() {
    return eventId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }
}
