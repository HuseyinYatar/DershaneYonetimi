package com.dershane.taskservice.task.adapter.in.web.dto;

import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The texts are masked in {@code toString} like every free-text object. */
public record TaskResponse(
    UUID id,
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
    List<UUID> attachmentFileIds,
    boolean active,
    Instant createdAt) {

  @Override
  public String toString() {
    return "TaskResponse[id=" + id + "]";
  }
}
