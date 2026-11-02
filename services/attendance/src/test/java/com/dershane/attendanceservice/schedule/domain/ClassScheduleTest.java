package com.dershane.attendanceservice.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassScheduleTest {

  // 2026-10-30 is a Friday.
  private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 30);

  private ClassSchedule fridays(LocalDate validFrom, LocalDate validTo) {
    return ClassSchedule.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        DayOfWeek.FRIDAY,
        LocalTime.of(9, 0),
        LocalTime.of(9, 40),
        validFrom,
        validTo);
  }

  @Test
  void callsForASessionOnItsWeekdayInsideTheValidRange() {
    ClassSchedule schedule = fridays(FRIDAY, FRIDAY.plusWeeks(2));

    assertThat(schedule.appliesOn(FRIDAY)).isTrue();
    assertThat(schedule.appliesOn(FRIDAY.plusWeeks(2))).isTrue();
    assertThat(schedule.appliesOn(FRIDAY.plusWeeks(3))).isFalse();
    assertThat(schedule.appliesOn(FRIDAY.minusWeeks(1))).isFalse();
    assertThat(schedule.appliesOn(FRIDAY.plusDays(1))).isFalse();
  }

  @Test
  void anOpenEndedScheduleNeverStops() {
    assertThat(fridays(FRIDAY, null).appliesOn(FRIDAY.plusWeeks(52))).isTrue();
  }

  @Test
  void aDeactivatedScheduleCallsForNothing() {
    ClassSchedule schedule = fridays(FRIDAY, null);

    assertThat(schedule.deactivate()).isTrue();
    assertThat(schedule.deactivate()).isFalse();
    assertThat(schedule.appliesOn(FRIDAY)).isFalse();
  }

  @Test
  void endTimeMustBeAfterStartTime() {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                ClassSchedule.create(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    DayOfWeek.FRIDAY,
                    LocalTime.of(10, 0),
                    LocalTime.of(9, 0),
                    FRIDAY,
                    null))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
  }

  @Test
  void validToMustNotBeBeforeValidFrom() {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> fridays(FRIDAY, FRIDAY.minusDays(1)))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
  }

  @Test
  void reviseKeepsBranchAndClassroomAndChecksTheRanges() {
    ClassSchedule schedule = fridays(FRIDAY, null);
    UUID classroomId = schedule.getClassroomId();
    UUID substitute = UUID.randomUUID();

    schedule.revise(
        substitute,
        schedule.getSubjectId(),
        DayOfWeek.MONDAY,
        LocalTime.of(14, 0),
        LocalTime.of(14, 40),
        FRIDAY,
        null);

    assertThat(schedule.getTeacherId()).isEqualTo(substitute);
    assertThat(schedule.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    assertThat(schedule.getClassroomId()).isEqualTo(classroomId);
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                schedule.revise(
                    substitute,
                    schedule.getSubjectId(),
                    DayOfWeek.MONDAY,
                    LocalTime.of(14, 40),
                    LocalTime.of(14, 0),
                    FRIDAY,
                    null));
  }
}
