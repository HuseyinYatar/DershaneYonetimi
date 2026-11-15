package com.dershane.taskservice.roster.adapter.out.persistence;

import com.dershane.taskservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "guardian_link_replica")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class GuardianLinkReplicaJpaEntity extends AuditableJpaEntity {

  @Id
  @Column(name = "student_id")
  @ToString.Include
  private UUID studentId;

  @Column(name = "guardian_id", nullable = false)
  private UUID guardianId;

  /** Time of the event that set this guardian; null for rows copied before the column existed. */
  @Column(name = "linked_at")
  private Instant linkedAt;
}
