import { describe, expect, it } from "vitest";
import { attendanceRate } from "./attendance";

describe("attendanceRate", () => {
  it("counts present and late as attended", () => {
    expect(attendanceRate({ present: 8, late: 1, absent: 1, excused: 0, total: 10 })).toBe(90);
  });

  it("is null when nothing was recorded", () => {
    expect(attendanceRate({ total: 0 })).toBeNull();
    expect(attendanceRate(undefined)).toBeNull();
  });
});
