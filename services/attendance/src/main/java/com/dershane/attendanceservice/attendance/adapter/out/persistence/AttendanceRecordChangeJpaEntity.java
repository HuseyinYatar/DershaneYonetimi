package com.dershane.attendanceservice.attendance.adapter.out.persistence;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
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
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

/** Append-only audit line; every column is insert-only. */
@Entity
@Table(name = "attendance_record_change")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class AttendanceRecordChangeJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "record_id", nullable = false, updatable = false)
  private UUID recordId;

  @Enumerated(EnumType.STRING)
  @Column(name = "old_status", length = 16, updatable = false)
  private AttendanceStatus oldStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status", nullable = false, length = 16, updatable = false)
  private AttendanceStatus newStatus;

  @Column(name = "changed_by", nullable = false, updatable = false)
  private UUID changedBy;

  @Column(name = "reason", nullable = false, length = 500, updatable = false)
  private String reason;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;
}
