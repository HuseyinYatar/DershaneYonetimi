package com.dershane.messagingservice.directory.adapter.out.persistence;

import com.dershane.messagingservice.directory.domain.UserRole;
import com.dershane.messagingservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "user_replica")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class UserReplicaJpaEntity extends AuditableJpaEntity {

  @Id
  @Column(name = "user_id")
  @ToString.Include
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private UserRole role;

  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "record_version", nullable = false)
  private long recordVersion;
}
