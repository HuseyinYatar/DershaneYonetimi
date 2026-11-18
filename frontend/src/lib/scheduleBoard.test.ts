import { describe, expect, it } from "vitest";
import { buildSlots, DEFAULT_TIMETABLE, overlaps, spanOf, timesOf } from "./scheduleBoard";

const SLOTS = buildSlots(DEFAULT_TIMETABLE);

describe("scheduleBoard", () => {
  it("builds the default grid: 50-minute lessons from 08:10, 10-minute breaks", () => {
    expect(SLOTS).toHaveLength(12);
    expect(SLOTS[0]).toEqual({ startsAt: "08:10", endsAt: "09:00" });
    expect(SLOTS[11].endsAt).toBe("20:00");
  });

  it("builds a grid from the branch's own setting", () => {
    const slots = buildSlots({
      firstLessonStart: "09:00",
      lessonMinutes: 40,
      breakMinutes: 15,
      lessonCount: 3,
      lunchLesson: 0,
    });
    expect(slots).toEqual([
      { startsAt: "09:00", endsAt: "09:40" },
      { startsAt: "09:55", endsAt: "10:35" },
      { startsAt: "10:50", endsAt: "11:30" },
    ]);
  });

  it("maps a time range to the slots it covers and back", () => {
    const span = spanOf(SLOTS, "10:10", "12:00");
    expect(span).toEqual({ first: 2, last: 3 });
    expect(timesOf(SLOTS, span!)).toEqual({ startsAt: "10:10", endsAt: "12:00" });
  });

  it("snaps a range that does not line up with the grid", () => {
    expect(spanOf(SLOTS, "10:00", "10:30")).toEqual({ first: 2, last: 2 });
  });

  it("returns nothing for a range outside the grid", () => {
    expect(spanOf(SLOTS, "21:00", "22:00")).toBeUndefined();
  });

  it("detects overlaps on the same day only, ignoring the lesson being moved", () => {
    const placed = [{ id: "a", dayOfWeek: "MONDAY", span: { first: 2, last: 3 } }];
    expect(overlaps(placed, "MONDAY", { first: 3, last: 4 })).toBe(true);
    expect(overlaps(placed, "MONDAY", { first: 4, last: 4 })).toBe(false);
    expect(overlaps(placed, "TUESDAY", { first: 2, last: 3 })).toBe(false);
    expect(overlaps(placed, "MONDAY", { first: 2, last: 3 }, "a")).toBe(false);
  });
});
