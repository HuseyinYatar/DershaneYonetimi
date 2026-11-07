package com.dershane.taskservice.assignment.adapter.out.persistence;

import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.shared.adapter.out.persistence.AuditableJpaEntity;
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
@Table(name = "assignment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class AssignmentJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "task_id", nullable = false, updatable = false)
  private UUID taskId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "occurrence_date", nullable = false, updatable = false)
  private LocalDate occurrenceDate;

  @Column(name = "due_at", nullable = false, updatable = false)
  private Instant dueAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AssignmentStatus status;

  @Column(name = "reminder_sent", nullable = false)
  private boolean reminderSent;

  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;
}
