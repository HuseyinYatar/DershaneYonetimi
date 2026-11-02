package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import com.dershane.attendanceservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
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
@Table(name = "class_schedule")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class ClassScheduleJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "classroom_id", nullable = false, updatable = false)
  private UUID classroomId;

  @Column(name = "teacher_id", nullable = false)
  private UUID teacherId;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Enumerated(EnumType.STRING)
  @Column(name = "day_of_week", nullable = false, length = 9)
  private DayOfWeek dayOfWeek;

  @Column(name = "starts_at", nullable = false)
  private LocalTime startsAt;

  @Column(name = "ends_at", nullable = false)
  private LocalTime endsAt;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  @Column(name = "active", nullable = false)
  private boolean active;
}
