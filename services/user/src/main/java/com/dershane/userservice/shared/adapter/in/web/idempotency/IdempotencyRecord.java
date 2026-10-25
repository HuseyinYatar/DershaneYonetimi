package com.dershane.userservice.shared.adapter.in.web.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class IdempotencyRecord {

  /** Lifecycle of a keyed request. */
  public enum Status {
    IN_PROGRESS,
    COMPLETED
  }

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "caller_id", nullable = false, updatable = false)
  private UUID callerId;

  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  @ToString.Include
  private Status status;

  @Column(name = "response_status")
  private Integer responseStatus;

  /** Response body without secrets (see {@link ReplaySafe}). May contain personal data. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_body")
  private String responseBody;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  public static IdempotencyRecord start(
      UUID callerId, String key, String requestHash, Instant now, Instant expiresAt) {
    IdempotencyRecord record = new IdempotencyRecord();
    record.callerId = callerId;
    record.idempotencyKey = key;
    record.requestHash = requestHash;
    record.status = Status.IN_PROGRESS;
    record.createdAt = now;
    record.expiresAt = expiresAt;
    return record;
  }

  public void complete(int httpStatus, String body) {
    this.status = Status.COMPLETED;
    this.responseStatus = httpStatus;
    this.responseBody = body;
  }
}
