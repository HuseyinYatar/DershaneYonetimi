import { describe, expect, it } from "vitest";
import { overlaps, SLOT_COUNT, SLOTS, spanOf, timesOf } from "./scheduleBoard";

describe("scheduleBoard", () => {
  it("builds 50-minute slots from 08:10", () => {
    expect(SLOTS[0]).toEqual({ startsAt: "08:10", endsAt: "09:00" });
    expect(SLOTS[SLOT_COUNT - 1].endsAt).toBe("20:00");
  });

  it("maps a time range to the slots it covers and back", () => {
    const span = spanOf("10:10", "12:00");
    expect(span).toEqual({ first: 2, last: 3 });
    expect(timesOf(span!)).toEqual({ startsAt: "10:10", endsAt: "12:00" });
  });

  it("snaps a range that does not line up with the grid", () => {
    expect(spanOf("10:00", "10:30")).toEqual({ first: 2, last: 2 });
  });

  it("returns nothing for a range outside the grid", () => {
    expect(spanOf("21:00", "22:00")).toBeUndefined();
  });

  it("detects overlaps on the same day only, ignoring the lesson being moved", () => {
    const placed = [{ id: "a", dayOfWeek: "MONDAY", span: { first: 2, last: 3 } }];
    expect(overlaps(placed, "MONDAY", { first: 3, last: 4 })).toBe(true);
    expect(overlaps(placed, "MONDAY", { first: 4, last: 4 })).toBe(false);
    expect(overlaps(placed, "TUESDAY", { first: 2, last: 3 })).toBe(false);
    expect(overlaps(placed, "MONDAY", { first: 2, last: 3 }, "a")).toBe(false);
  });
});
