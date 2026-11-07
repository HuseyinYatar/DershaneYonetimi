package com.dershane.taskservice.task.domain;

import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.shared.domain.Rejections;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A teacher's task (diagram: Gorev). The target, dates and recurrence never change after creation,
 * so assignments already handed out stay consistent; only the texts and attachments can be edited.
 */
@Slf4j
@Getter
public class Task {

  /** Null until the task is saved for the first time. */
  private final UUID id;

  /** Version the task was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID branchId;
  private final UUID teacherId;
  private String title;
  private String description;
  private final TargetType targetType;
  private final UUID targetId;
  private final Recurrence recurrence;
  private final Set<DayOfWeek> recurrenceDays;
  private final LocalDate startDate;
  private final LocalDate endDate;
  private final LocalTime dueTime;
  private List<UUID> attachmentFileIds;
  private boolean active;

  /** Rehydrates a saved task. Use {@link #create} for a new one. */
  // A rehydrating constructor takes every field of the aggregate.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public Task(
      UUID id,
      Long version,
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
      boolean active) {
    this.id = id;
    this.version = version;
    this.branchId = branchId;
    this.teacherId = teacherId;
    this.title = title;
    this.description = description;
    this.targetType = targetType;
    this.targetId = targetId;
    this.recurrence = recurrence;
    this.recurrenceDays = recurrenceDays;
    this.startDate = startDate;
    this.endDate = endDate;
    this.dueTime = dueTime;
    this.attachmentFileIds = attachmentFileIds;
    this.active = active;
  }

  /**
   * Validates the dates and recurrence of a new task.
   *
   * @param today the institution's current date
   * @param now the current instant
   * @param zone the institution's time zone, which due times are read in
   */
  public static Task create(TaskDraft draft, LocalDate today, Instant now, ZoneId zone) {
    requireValidRecurrence(draft);
    if (draft.endDate() != null && draft.endDate().isBefore(draft.startDate())) {
      throw Rejections.reject(
          log,
          ErrorCode.INVALID_DATE_RANGE,
          "Task rejected: the end date is before the start date",
          "teacherId",
          draft.teacherId());
    }
    if (draft.startDate().isBefore(today)) {
      throw Rejections.reject(
          log,
          ErrorCode.START_DATE_IN_PAST,
          "Task rejected: the start date is in the past",
          "teacherId",
          draft.teacherId());
    }
    Task task =
        new Task(
            null,
            null,
            draft.branchId(),
            draft.teacherId(),
            draft.title().strip(),
            draft.description() == null ? "" : draft.description().strip(),
            draft.targetType(),
            draft.targetId(),
            draft.recurrence(),
            days(draft),
            draft.startDate(),
            draft.endDate(),
            draft.dueTime(),
            List.copyOf(draft.attachmentFileIds()),
            true);
    if (draft.recurrence() == Recurrence.ONCE
        && !task.dueAt(draft.startDate(), zone).isAfter(now)) {
      throw Rejections.reject(
          log,
          ErrorCode.DUE_IN_PAST,
          "Task rejected: the due time is not in the future",
          "teacherId",
          draft.teacherId());
    }
    return task;
  }

  private static Set<DayOfWeek> days(TaskDraft draft) {
    return draft.recurrenceDays().isEmpty()
        ? EnumSet.noneOf(DayOfWeek.class)
        : EnumSet.copyOf(draft.recurrenceDays());
  }

  private static void requireValidRecurrence(TaskDraft draft) {
    boolean hasDays = !draft.recurrenceDays().isEmpty();
    boolean weekly = draft.recurrence() == Recurrence.WEEKLY;
    if (weekly != hasDays) {
      throw Rejections.reject(
          log,
          ErrorCode.INVALID_RECURRENCE,
          "Task rejected: weekdays are required for weekly tasks and forbidden otherwise",
          "teacherId",
          draft.teacherId(),
          "recurrence",
          draft.recurrence());
    }
    if (draft.recurrence() == Recurrence.ONCE && draft.endDate() == null) {
      throw Rejections.reject(
          log,
          ErrorCode.INVALID_RECURRENCE,
          "Task rejected: a one-off task needs an end date (its due day)",
          "teacherId",
          draft.teacherId());
    }
  }

  /** Whether the task hands out an assignment on {@code date} (ignores {@link #isActive}). */
  public boolean occursOn(LocalDate date) {
    if (recurrence == Recurrence.ONCE) {
      return date.equals(startDate);
    }
    boolean inRange = !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    return inRange
        && (recurrence == Recurrence.DAILY || recurrenceDays.contains(date.getDayOfWeek()));
  }

  /** The due instant of the assignment handed out for {@code occurrence}. */
  public Instant dueAt(LocalDate occurrence, ZoneId zone) {
    LocalDate dueDate = recurrence == Recurrence.ONCE ? endDate : occurrence;
    return dueDate.atTime(dueTime).atZone(zone).toInstant();
  }

  /** The date an assignment of this task is filed under when it is handed out on {@code day}. */
  public LocalDate occurrenceFor(LocalDate day) {
    return recurrence == Recurrence.ONCE ? startDate : day;
  }

  public void edit(String newTitle, String newDescription, List<UUID> newAttachmentFileIds) {
    this.title = newTitle.strip();
    this.description = newDescription == null ? "" : newDescription.strip();
    this.attachmentFileIds = List.copyOf(newAttachmentFileIds);
  }

  /** Stops new assignments; the existing ones stay. */
  public void deactivate() {
    if (!active) {
      throw Rejections.reject(
          log, ErrorCode.TASK_INACTIVE, "Task is already inactive", "taskId", id);
    }
    this.active = false;
  }

  @Override
  public String toString() {
    return "Task[id=" + id + "]";
  }
}
