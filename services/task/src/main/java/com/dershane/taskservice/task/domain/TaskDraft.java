package com.dershane.taskservice.task.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a teacher chooses for a new task. The title and description are teacher text, not
 * personal data, but the record is still masked in {@code toString} like every free-text object.
 */
public record TaskDraft(
    UUID branchId,
    UUID teacherId,
    String title,
    String description,
    TargetType targetType,
    UUID targetId,
    Recurrence recurrence,
    Set<DayOfWeek> recurrenceDays,
    LocalDate startDate,
    LocalDate endDate,
    LocalTime dueTime,
    List<UUID> attachmentFileIds) {

  @Override
  public String toString() {
    return "TaskDraft[teacherId=" + teacherId + "]";
  }
}
