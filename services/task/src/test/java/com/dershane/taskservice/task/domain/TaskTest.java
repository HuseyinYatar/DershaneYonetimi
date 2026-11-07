package com.dershane.taskservice.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.taskservice.shared.domain.DomainException;
import com.dershane.taskservice.shared.domain.ErrorCode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskTest {

  private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
  // A Monday.
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
  private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");

  private static TaskDraft draft(
      Recurrence recurrence, Set<DayOfWeek> days, LocalDate start, LocalDate end) {
    return new TaskDraft(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "  Chapter 4  ",
        null,
        TargetType.CLASSROOM,
        UUID.randomUUID(),
        recurrence,
        days,
        start,
        end,
        LocalTime.of(20, 0),
        List.of());
  }

  private static Task create(TaskDraft draft) {
    return Task.create(draft, TODAY, NOW, ZONE);
  }

  private static void assertRejected(Runnable action, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void aNewTaskIsActiveWithTrimmedTexts() {
    Task task = create(draft(Recurrence.DAILY, Set.of(), TODAY, null));

    assertThat(task.isActive()).isTrue();
    assertThat(task.getTitle()).isEqualTo("Chapter 4");
    assertThat(task.getDescription()).isEmpty();
    assertThat(task.getId()).isNull();
  }

  @Test
  void aOneOffTaskOccursOnlyOnItsStartDateAndIsDueOnTheEndDate() {
    Task task = create(draft(Recurrence.ONCE, Set.of(), TODAY, TODAY.plusDays(3)));

    assertThat(task.occursOn(TODAY)).isTrue();
    assertThat(task.occursOn(TODAY.plusDays(1))).isFalse();
    assertThat(task.occurrenceFor(TODAY.plusDays(2))).isEqualTo(TODAY);
    assertThat(task.dueAt(TODAY, ZONE)).isEqualTo(Instant.parse("2026-11-12T17:00:00Z"));
  }

  @Test
  void aDailyTaskOccursEveryDayOfItsRangeAndIsDueTheSameDay() {
    Task task = create(draft(Recurrence.DAILY, Set.of(), TODAY, TODAY.plusDays(2)));

    assertThat(task.occursOn(TODAY.minusDays(1))).isFalse();
    assertThat(task.occursOn(TODAY)).isTrue();
    assertThat(task.occursOn(TODAY.plusDays(2))).isTrue();
    assertThat(task.occursOn(TODAY.plusDays(3))).isFalse();
    assertThat(task.occurrenceFor(TODAY.plusDays(1))).isEqualTo(TODAY.plusDays(1));
    assertThat(task.dueAt(TODAY.plusDays(1), ZONE))
        .isEqualTo(Instant.parse("2026-11-10T17:00:00Z"));
  }

  @Test
  void anOpenEndedDailyTaskKeepsOccurring() {
    Task task = create(draft(Recurrence.DAILY, Set.of(), TODAY, null));

    assertThat(task.occursOn(TODAY.plusYears(1))).isTrue();
  }

  @Test
  void aWeeklyTaskOccursOnlyOnItsWeekdays() {
    Task task =
        create(
            draft(
                Recurrence.WEEKLY,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                TODAY,
                TODAY.plusDays(20)));

    assertThat(task.occursOn(TODAY)).isTrue();
    assertThat(task.occursOn(TODAY.plusDays(1))).isFalse();
    assertThat(task.occursOn(TODAY.plusDays(3))).isTrue();
    assertThat(task.occursOn(TODAY.plusDays(7))).isTrue();
    assertThat(task.occursOn(TODAY.plusDays(21))).isFalse();
  }

  @Test
  void weekdaysAreRequiredForWeeklyAndForbiddenOtherwise() {
    assertRejected(
        () -> create(draft(Recurrence.WEEKLY, Set.of(), TODAY, null)),
        ErrorCode.INVALID_RECURRENCE);
    assertRejected(
        () -> create(draft(Recurrence.DAILY, Set.of(DayOfWeek.MONDAY), TODAY, null)),
        ErrorCode.INVALID_RECURRENCE);
    assertRejected(
        () -> create(draft(Recurrence.ONCE, Set.of(DayOfWeek.MONDAY), TODAY, TODAY)),
        ErrorCode.INVALID_RECURRENCE);
  }

  @Test
  void aOneOffTaskNeedsAnEndDate() {
    assertRejected(
        () -> create(draft(Recurrence.ONCE, Set.of(), TODAY, null)), ErrorCode.INVALID_RECURRENCE);
  }

  @Test
  void theEndDateMayNotPrecedeTheStartDate() {
    assertRejected(
        () -> create(draft(Recurrence.DAILY, Set.of(), TODAY.plusDays(2), TODAY.plusDays(1))),
        ErrorCode.INVALID_DATE_RANGE);
  }

  @Test
  void theStartDateMayNotBeInThePast() {
    assertRejected(
        () -> create(draft(Recurrence.DAILY, Set.of(), TODAY.minusDays(1), null)),
        ErrorCode.START_DATE_IN_PAST);
  }

  @Test
  void aOneOffTaskDueInThePastIsRejected() {
    Instant afterDue = Instant.parse("2026-11-09T17:30:00Z");

    assertRejected(
        () -> Task.create(draft(Recurrence.ONCE, Set.of(), TODAY, TODAY), TODAY, afterDue, ZONE),
        ErrorCode.DUE_IN_PAST);
  }

  @Test
  void editingChangesOnlyTextsAndAttachments() {
    Task task = create(draft(Recurrence.DAILY, Set.of(), TODAY, null));
    UUID file = UUID.randomUUID();

    task.edit(" New title ", " New text ", List.of(file));

    assertThat(task.getTitle()).isEqualTo("New title");
    assertThat(task.getDescription()).isEqualTo("New text");
    assertThat(task.getAttachmentFileIds()).containsExactly(file);
    assertThat(task.getRecurrence()).isEqualTo(Recurrence.DAILY);
  }

  @Test
  void deactivatingTwiceIsRejected() {
    Task task = create(draft(Recurrence.DAILY, Set.of(), TODAY, null));

    task.deactivate();

    assertThat(task.isActive()).isFalse();
    assertRejected(task::deactivate, ErrorCode.TASK_INACTIVE);
  }
}
