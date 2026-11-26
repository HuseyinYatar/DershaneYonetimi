package com.dershane.attendanceservice.schedule.domain;

/**
 * A run of consecutive lesson hours of a {@link TimetableGrid}, by 0-based index, both inclusive.
 */
public record SlotSpan(int first, int last) {

  public int size() {
    return last - first + 1;
  }

  public boolean overlaps(SlotSpan other) {
    return first <= other.last && other.first <= last;
  }

  public boolean contains(int slot) {
    return first <= slot && slot <= last;
  }
}
