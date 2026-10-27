package com.dershane.userservice.shared.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Audit columns shared by every entity: who changed a record and when (KVKK accountability).
 *
 * <p>{@code version} is a wrapper type on purpose: Spring Data treats {@code version == null} as a
 * new entity, so entities with assigned ids (Keycloak sub) are persisted instead of merged. None of
 * these fields has a setter: the mappers cannot overwrite them.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableJpaEntity {

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @LastModifiedBy
  @Column(name = "updated_by")
  private UUID updatedBy;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;
}
