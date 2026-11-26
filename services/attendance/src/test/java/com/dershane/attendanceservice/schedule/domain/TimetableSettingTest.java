package com.dershane.attendanceservice.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimetableSettingTest {

  private final UUID branchId = UUID.randomUUID();

  private TimetableSetting of(LocalTime start, int lesson, int pause, int count) {
    return TimetableSetting.of(branchId, null, start, lesson, pause, count, 0);
  }

  private void assertRejected(LocalTime start, int lesson, int pause, int count) {
    assertRejected(start, lesson, pause, count, 0);
  }

  private void assertRejected(LocalTime start, int lesson, int pause, int count, int lunch) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> TimetableSetting.of(branchId, null, start, lesson, pause, count, lunch))
        .satisfies(
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TIMETABLE_SETTING_INVALID));
  }

  @Test
  void defaultsAreTheFormerFixedGrid() {
    TimetableSetting defaults = TimetableSetting.defaults(branchId);

    assertThat(defaults.getFirstLessonStart()).isEqualTo(LocalTime.of(8, 10));
    assertThat(defaults.getLessonMinutes()).isEqualTo(50);
    assertThat(defaults.getBreakMinutes()).isEqualTo(10);
    assertThat(defaults.getLessonCount()).isEqualTo(12);
    assertThat(defaults.getLunchLesson()).isZero();
    assertThat(defaults.getVersion()).isNull();
  }

  @Test
  void acceptsAGridThatEndsInsideTheDay() {
    TimetableSetting setting = TimetableSetting.of(branchId, 4L, LocalTime.of(9, 0), 40, 15, 3, 0);

    assertThat(setting.getVersion()).isEqualTo(4L);
    assertThat(setting.getLessonCount()).isEqualTo(3);
  }

  @Test
  void acceptsTheBounds() {
    of(LocalTime.of(5, 0), 20, 0, 1);
    of(LocalTime.of(5, 0), 120, 60, 5);
    of(LocalTime.of(8, 0), 50, 10, 16);
  }

  @Test
  void firstLessonMustStartBetween0500And2000() {
    assertRejected(LocalTime.of(4, 59), 50, 10, 4);
    assertRejected(LocalTime.of(20, 1), 20, 0, 1);
  }

  @Test
  void lessonLengthMustBeBetween20And120Minutes() {
    assertRejected(LocalTime.of(8, 0), 19, 10, 4);
    assertRejected(LocalTime.of(8, 0), 121, 10, 4);
  }

  @Test
  void breakLengthMustBeBetween0And60Minutes() {
    assertRejected(LocalTime.of(8, 0), 50, -1, 4);
    assertRejected(LocalTime.of(8, 0), 50, 61, 4);
  }

  @Test
  void lessonCountMustBeBetween1And16() {
    assertRejected(LocalTime.of(8, 0), 50, 10, 0);
    assertRejected(LocalTime.of(8, 0), 50, 10, 17);
  }

  @Test
  void theLastLessonMustEndInsideTheDay() {
    // 20:00 + 5 x 50 min = 00:10 next day.
    assertRejected(LocalTime.of(20, 0), 50, 0, 5);
    // 20:00 + 3 x 60 min = 23:00 is fine; 4 x 60 min ends at 00:00.
    of(LocalTime.of(20, 0), 60, 0, 3);
    assertRejected(LocalTime.of(20, 0), 60, 0, 4);
  }

  @Test
  void anyLessonHourOfTheDayCanBeTheLunchBreak() {
    TimetableSetting setting =
        TimetableSetting.of(branchId, null, LocalTime.of(8, 10), 50, 10, 8, 5);

    assertThat(setting.getLunchLesson()).isEqualTo(5);
    TimetableSetting.of(branchId, null, LocalTime.of(8, 0), 50, 10, 6, 1);
    TimetableSetting.of(branchId, null, LocalTime.of(8, 0), 50, 10, 6, 6);
  }

  @Test
  void lunchMustBeNoneOrOneOfTheLessonHours() {
    assertRejected(LocalTime.of(8, 0), 50, 10, 8, 9);
    assertRejected(LocalTime.of(8, 0), 50, 10, 8, -1);
  }

  @Test
  void shrinkingTheLessonCountBelowTheLunchHourIsRejected() {
    // A branch that had lunch at hour 10 of 12 lessons cannot keep it with 8 lessons.
    assertRejected(LocalTime.of(8, 0), 50, 10, 8, 10);
  }

  @Test
  void lunchDoesNotChangeTheEndOfTheDayCheck() {
    // 20:00 + 3 x 60 min = 23:00 fits with or without a lunch hour; 4 x 60 min ends at 00:00 either
    // way.
    TimetableSetting.of(branchId, null, LocalTime.of(20, 0), 60, 0, 3, 2);
    assertRejected(LocalTime.of(20, 0), 60, 0, 4, 2);
  }
}
