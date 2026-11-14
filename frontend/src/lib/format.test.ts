import { describe, expect, it } from "vitest";
import {
  formatDateLong,
  formatDeadline,
  formatDelta,
  formatFullDateWithWeekday,
  formatInt,
  formatNet,
  formatPercent,
  formatRelativeDeadline,
  greetingFor,
  initialsOf,
  parseDateOnly,
  ratioToPercent,
} from "./format";

describe("number formatting", () => {
  it("uses a decimal comma with one digit for nets", () => {
    expect(formatNet(72.5)).toBe("72,5");
    expect(formatNet("22.00")).toBe("22,0");
    expect(formatNet("1234.5")).toBe("1.234,5");
  });

  it("renders missing or non-numeric values as an em dash", () => {
    expect(formatNet(null)).toBe("—");
    expect(formatNet(undefined)).toBe("—");
    expect(formatNet("abc")).toBe("—");
    expect(formatInt("")).toBe("—");
  });

  it("puts the percent sign first", () => {
    expect(formatPercent(94)).toBe("%94");
    expect(formatPercent(93.6)).toBe("%94");
  });

  it("converts ratios to whole percents", () => {
    expect(ratioToPercent("0.3800")).toBe(38);
    expect(ratioToPercent(null)).toBe(0);
  });
});

describe("date formatting", () => {
  it("formats the header date as weekday, day month", () => {
    expect(formatDateLong(new Date(2026, 10, 13))).toBe("Cuma, 13 Kasım");
  });

  it("formats deadlines as short date and 24-hour time", () => {
    expect(formatDeadline(new Date(2026, 10, 12, 23, 59))).toBe("12 Kas, 23:59");
  });

  it("formats exam dates with the weekday", () => {
    expect(formatFullDateWithWeekday(new Date(2026, 10, 8))).toBe("8 Kasım 2026, Pazar");
  });

  it("parses date-only values as local dates", () => {
    const d = parseDateOnly("2026-11-08");
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 10, 8]);
  });
});

describe("greetingFor", () => {
  it.each([
    [5, "Günaydın"],
    [11, "Günaydın"],
    [12, "İyi günler"],
    [17, "İyi günler"],
    [18, "İyi akşamlar"],
    [23, "İyi akşamlar"],
    [0, "İyi akşamlar"],
    [4, "İyi akşamlar"],
  ])("hour %i -> %s", (hour, greeting) => {
    expect(greetingFor(hour)).toBe(greeting);
  });
});

describe("initialsOf", () => {
  it("uppercases with the Turkish locale", () => {
    expect(initialsOf("ayşe", "yıldız")).toBe("AY");
    expect(initialsOf("ismail", "kaya")).toBe("İK");
  });

  it("falls back to a placeholder", () => {
    expect(initialsOf(undefined, undefined)).toBe("?");
  });
});

describe("formatRelativeDeadline", () => {
  const now = new Date(2026, 10, 13, 9, 0);

  it("says today and tomorrow", () => {
    expect(formatRelativeDeadline(new Date(2026, 10, 13, 23, 59), now)).toBe("Bugün · 23:59");
    expect(formatRelativeDeadline(new Date(2026, 10, 14, 23, 59), now)).toBe("Yarın · 23:59");
  });

  it("spells out later days", () => {
    expect(formatRelativeDeadline(new Date(2026, 10, 17, 18, 0), now)).toBe(
      "Salı, 17 Kasım · 18:00",
    );
  });
});

describe("formatDelta", () => {
  it("keeps up to two decimals", () => {
    expect(formatDelta(4.25)).toBe("4,25");
    expect(formatDelta(3)).toBe("3");
  });
});
