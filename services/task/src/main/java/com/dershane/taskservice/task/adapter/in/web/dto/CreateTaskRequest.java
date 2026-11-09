package com.dershane.taskservice.task.adapter.in.web.dto;

import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The texts are masked in {@code toString} like every free-text object. */
public record CreateTaskRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    @NotNull TargetType targetType,
    @NotNull UUID targetId,
    @NotNull Recurrence recurrence,
    @Size(max = 7) Set<@NotNull DayOfWeek> recurrenceDays,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @NotNull LocalTime dueTime,
    @Size(max = 50) List<@NotNull UUID> attachmentFileIds) {

  @Override
  public String toString() {
    return "CreateTaskRequest[targetId=" + targetId + "]";
  }
}
