package com.dershane.attendanceservice.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimetableGridTest {

  private final UUID branch = UUID.randomUUID();
  private final TimetableGrid grid = TimetableGrid.of(TimetableSetting.defaults(branch));

  private static LocalTime t(String hhmm) {
    return LocalTime.parse(hhmm);
  }

  @Test
  void laysOutFiftyMinuteLessonsFromTenPastEight() {
    assertThat(grid.slotCount()).isEqualTo(12);
    assertThat(grid.startOf(new SlotSpan(0, 0))).isEqualTo(t("08:10"));
    assertThat(grid.endOf(new SlotSpan(0, 0))).isEqualTo(t("09:00"));
    assertThat(grid.endOf(new SlotSpan(11, 11))).isEqualTo(t("20:00"));
  }

  @Test
  void theLunchHourIsTheOneBeforeItsOneBasedNumber() {
    TimetableGrid lunch =
        TimetableGrid.of(new TimetableSetting(branch, 1L, LocalTime.of(8, 10), 50, 10, 12, 5));

    assertThat(lunch.lunchSlot()).isEqualTo(4);
    assertThat(lunch.coversLunch(new SlotSpan(3, 4))).isTrue();
    assertThat(lunch.coversLunch(new SlotSpan(5, 6))).isFalse();
    assertThat(grid.lunchSlot()).isEqualTo(-1);
    assertThat(grid.coversLunch(new SlotSpan(0, 11))).isFalse();
  }

  @Test
  void exactTimesMapToTheirHoursAndNothingElseDoes() {
    assertThat(grid.exactly(t("10:10"), t("12:00"))).contains(new SlotSpan(2, 3));
    assertThat(grid.exactly(t("10:00"), t("12:00"))).isEmpty();
    assertThat(grid.exactly(t("10:10"), t("11:30"))).isEmpty();
    assertThat(grid.exactly(t("12:00"), t("10:10"))).isEmpty();
  }

  @Test
  void aRangeThatDoesNotLineUpSnapsToTheHoursItTouches() {
    assertThat(grid.touchedBy(t("10:00"), t("10:30"))).contains(new SlotSpan(2, 2));
    assertThat(grid.touchedBy(t("10:10"), t("12:00"))).contains(new SlotSpan(2, 3));
  }

  @Test
  void aRangeOutsideTheGridTouchesNothing() {
    assertThat(grid.touchedBy(t("21:00"), t("22:00"))).isEmpty();
    assertThat(grid.touchedBy(t("06:00"), t("07:00"))).isEmpty();
  }

  @Test
  void aTimeRangeOverlapsTheLunchHourOnlyWhenItSharesAMinuteWithIt() {
    // Lunch is the 5th hour: 12:10-13:00.
    TimetableGrid lunch =
        TimetableGrid.of(new TimetableSetting(branch, 1L, LocalTime.of(8, 10), 50, 10, 12, 5));

    assertThat(lunch.overlapsLunch(t("12:30"), t("13:20"))).isTrue();
    assertThat(lunch.overlapsLunch(t("11:00"), t("12:20"))).isTrue();
    assertThat(lunch.overlapsLunch(t("13:00"), t("13:50"))).isFalse();
    assertThat(lunch.overlapsLunch(t("11:10"), t("12:10"))).isFalse();
    assertThat(grid.overlapsLunch(t("12:10"), t("13:00"))).isFalse();
  }
}
