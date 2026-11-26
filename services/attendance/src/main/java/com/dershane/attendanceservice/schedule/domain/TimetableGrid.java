package com.dershane.attendanceservice.schedule.domain;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The lesson hours of a day laid out from a branch's {@link TimetableSetting}: lesson {@code i}
 * starts at {@code firstLessonStart + i * (lessonMinutes + breakMinutes)} and lasts a lesson. The
 * lunch lesson hour, if any, is kept free of lessons.
 */
public final class TimetableGrid {

  /** One lesson hour. */
  public record Slot(LocalTime startsAt, LocalTime endsAt) {}

  private final List<Slot> slots;
  private final int lunchSlot;

  private TimetableGrid(List<Slot> slots, int lunchSlot) {
    this.slots = slots;
    this.lunchSlot = lunchSlot;
  }

  public static TimetableGrid of(TimetableSetting setting) {
    List<Slot> slots = new ArrayList<>();
    for (int i = 0; i < setting.getLessonCount(); i++) {
      LocalTime start =
          setting
              .getFirstLessonStart()
              .plusMinutes((long) i * (setting.getLessonMinutes() + setting.getBreakMinutes()));
      slots.add(new Slot(start, start.plusMinutes(setting.getLessonMinutes())));
    }
    return new TimetableGrid(List.copyOf(slots), setting.getLunchLesson() - 1);
  }

  public int slotCount() {
    return slots.size();
  }

  /** 0-based index of the lunch hour, or -1 when the branch has none. */
  public int lunchSlot() {
    return lunchSlot;
  }

  public boolean coversLunch(SlotSpan span) {
    return lunchSlot >= 0 && span.contains(lunchSlot);
  }

  /** True when the time range shares any minute with the lunch hour (no lunch hour: false). */
  public boolean overlapsLunch(LocalTime startsAt, LocalTime endsAt) {
    if (lunchSlot < 0 || lunchSlot >= slots.size()) {
      return false;
    }
    Slot lunch = slots.get(lunchSlot);
    return startsAt.isBefore(lunch.endsAt()) && lunch.startsAt().isBefore(endsAt);
  }

  public LocalTime startOf(SlotSpan span) {
    return slots.get(span.first()).startsAt();
  }

  public LocalTime endOf(SlotSpan span) {
    return slots.get(span.last()).endsAt();
  }

  /**
   * The hours a time range touches: from the first hour that ends after {@code startsAt} to the
   * last that starts before {@code endsAt}. Empty when the range falls outside the grid.
   */
  public Optional<SlotSpan> touchedBy(LocalTime startsAt, LocalTime endsAt) {
    int first = -1;
    int last = -1;
    for (int i = 0; i < slots.size(); i++) {
      if (first == -1 && slots.get(i).endsAt().isAfter(startsAt)) {
        first = i;
      }
      if (slots.get(i).startsAt().isBefore(endsAt)) {
        last = i;
      }
    }
    return first == -1 || last < first ? Optional.empty() : Optional.of(new SlotSpan(first, last));
  }

  /** The hours whose start and end are exactly the given times; empty when they do not line up. */
  public Optional<SlotSpan> exactly(LocalTime startsAt, LocalTime endsAt) {
    int first = -1;
    int last = -1;
    for (int i = 0; i < slots.size(); i++) {
      if (slots.get(i).startsAt().equals(startsAt)) {
        first = i;
      }
      if (slots.get(i).endsAt().equals(endsAt)) {
        last = i;
      }
    }
    return first == -1 || last < first ? Optional.empty() : Optional.of(new SlotSpan(first, last));
  }
}
