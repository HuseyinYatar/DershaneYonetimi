package com.dershane.attendanceservice.roster.adapter.out.persistence;

import com.dershane.attendanceservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "membership_replica")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class MembershipReplicaJpaEntity extends AuditableJpaEntity {

  /** The user service's membership id; assigned, not generated. */
  @Id @ToString.Include private UUID id;

  @Column(name = "classroom_id", nullable = false, updatable = false)
  private UUID classroomId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "start_date", nullable = false, updatable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "student_number", nullable = false, length = 32, updatable = false)
  private String studentNumber;

  @Column(name = "full_name", nullable = false, length = 200, updatable = false)
  private String fullName;
}
