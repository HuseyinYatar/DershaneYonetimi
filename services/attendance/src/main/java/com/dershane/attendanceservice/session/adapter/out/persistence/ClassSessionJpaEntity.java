package com.dershane.attendanceservice.session.adapter.out.persistence;

import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "class_session")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class ClassSessionJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "schedule_id", updatable = false)
  private UUID scheduleId;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "classroom_id", nullable = false, updatable = false)
  private UUID classroomId;

  @Column(name = "teacher_id", nullable = false)
  private UUID teacherId;

  @Column(name = "subject_id", nullable = false, updatable = false)
  private UUID subjectId;

  @Column(name = "session_date", nullable = false, updatable = false)
  private LocalDate sessionDate;

  @Column(name = "starts_at", nullable = false, updatable = false)
  private LocalTime startsAt;

  @Column(name = "ends_at", nullable = false, updatable = false)
  private LocalTime endsAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private SessionStatus status;

  @Column(name = "taken_at")
  private Instant takenAt;

  @Column(name = "taken_by")
  private UUID takenBy;
}
