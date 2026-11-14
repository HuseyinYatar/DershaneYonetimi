import { describe, expect, it } from "vitest";
import { dateKey, shortDayName, startOfWeek, weekDays, weekRangeLabel } from "./week";

describe("week math", () => {
  it("starts the week on Monday", () => {
    // 2026-11-13 is a Friday, 2026-11-15 a Sunday, 2026-11-09 a Monday.
    expect(dateKey(startOfWeek(new Date(2026, 10, 13)))).toBe("2026-11-09");
    expect(dateKey(startOfWeek(new Date(2026, 10, 15)))).toBe("2026-11-09");
    expect(dateKey(startOfWeek(new Date(2026, 10, 9)))).toBe("2026-11-09");
  });

  it("lists seven days with Turkish short names", () => {
    const days = weekDays(new Date(2026, 10, 13));
    expect(days.map((d) => d.getDate())).toEqual([9, 10, 11, 12, 13, 14, 15]);
    expect(days.map(shortDayName)).toEqual(["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"]);
  });

  it("crosses month boundaries", () => {
    const days = weekDays(new Date(2026, 11, 2)); // Wednesday 2 Dec
    expect(dateKey(days[0])).toBe("2026-11-30");
    expect(dateKey(days[6])).toBe("2026-12-06");
  });

  it("labels the range", () => {
    expect(weekRangeLabel(weekDays(new Date(2026, 10, 13)))).toBe("9–15 Kasım");
    expect(weekRangeLabel(weekDays(new Date(2026, 11, 2)))).toBe("30 Kas – 6 Ara");
  });

  it("pads the date key", () => {
    expect(dateKey(new Date(2026, 0, 5))).toBe("2026-01-05");
  });
});
