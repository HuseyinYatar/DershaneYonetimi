package com.dershane.attendanceservice.attendance.adapter.out.persistence;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.shared.adapter.out.persistence.AuditableJpaEntity;
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

@Entity
@Table(name = "attendance_record")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class AttendanceRecordJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AttendanceStatus status;

  @Column(name = "marked_at", nullable = false)
  private Instant markedAt;

  @Column(name = "marked_by", nullable = false)
  private UUID markedBy;

  @Column(name = "excuse_id")
  private UUID excuseId;

  @Column(name = "note", length = 200, updatable = false)
  private String note;
}
