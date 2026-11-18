package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import com.dershane.attendanceservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** One draft per branch; the id is assigned by the domain. */
@Entity
@Table(name = "timetable_draft")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class TimetableDraftJpaEntity extends AuditableJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  @ToString.Include
  private UUID id;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;
}
