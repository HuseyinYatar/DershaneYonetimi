package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import com.dershane.attendanceservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** One row per branch; the branch id is the key (assigned, not generated). */
@Entity
@Table(name = "timetable_setting")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class TimetableSettingJpaEntity extends AuditableJpaEntity {

  @Id
  @Column(name = "branch_id", nullable = false, updatable = false)
  @ToString.Include
  private UUID branchId;

  @Column(name = "first_lesson_start", nullable = false)
  private LocalTime firstLessonStart;

  @Column(name = "lesson_minutes", nullable = false)
  private int lessonMinutes;

  @Column(name = "break_minutes", nullable = false)
  private int breakMinutes;

  @Column(name = "lesson_count", nullable = false)
  private int lessonCount;

  @Column(name = "lunch_lesson", nullable = false)
  private int lunchLesson;
}
