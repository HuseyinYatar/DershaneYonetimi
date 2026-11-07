package com.dershane.taskservice.task.adapter.out.persistence;

import com.dershane.taskservice.shared.adapter.out.persistence.AuditableJpaEntity;
import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class TaskJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "teacher_id", nullable = false, updatable = false)
  private UUID teacherId;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description", nullable = false, length = 5000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 16, updatable = false)
  private TargetType targetType;

  @Column(name = "target_id", nullable = false, updatable = false)
  private UUID targetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "recurrence", nullable = false, length = 16, updatable = false)
  private Recurrence recurrence;

  /** {@code DayOfWeek} names; converted by the mapper. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(
      name = "recurrence_days",
      nullable = false,
      updatable = false,
      columnDefinition = "varchar(9)[]")
  private String[] recurrenceDays;

  @Column(name = "start_date", nullable = false, updatable = false)
  private LocalDate startDate;

  @Column(name = "end_date", updatable = false)
  private LocalDate endDate;

  @Column(name = "due_time", nullable = false, updatable = false)
  private LocalTime dueTime;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "attachment_file_ids", nullable = false, columnDefinition = "uuid[]")
  private UUID[] attachmentFileIds;

  @Column(name = "active", nullable = false)
  private boolean active;
}
