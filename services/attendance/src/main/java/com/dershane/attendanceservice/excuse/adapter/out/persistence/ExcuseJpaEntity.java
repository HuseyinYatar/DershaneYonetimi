package com.dershane.attendanceservice.excuse.adapter.out.persistence;

import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import com.dershane.attendanceservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "excuse")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class ExcuseJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "from_date", nullable = false, updatable = false)
  private LocalDate fromDate;

  @Column(name = "to_date", nullable = false, updatable = false)
  private LocalDate toDate;

  @Column(name = "reason", nullable = false, length = 500, updatable = false)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private ExcuseStatus status;

  @Column(name = "requested_by", nullable = false, updatable = false)
  private UUID requestedBy;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_note", length = 500)
  private String decisionNote;
}
