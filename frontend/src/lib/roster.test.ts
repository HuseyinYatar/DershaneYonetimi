import { describe, expect, it } from "vitest";
import { allMarked, markAll, markedCount, toEntries } from "./roster";

const IDS = ["a", "b", "c"];

describe("roster marks", () => {
  it("is complete only when every student has a status", () => {
    expect(allMarked(IDS, {})).toBe(false);
    expect(allMarked(IDS, { a: "PRESENT", b: "ABSENT" })).toBe(false);
    expect(allMarked(IDS, { a: "PRESENT", b: "ABSENT", c: "LATE" })).toBe(true);
  });

  it("never counts an empty roster as complete", () => {
    expect(allMarked([], {})).toBe(false);
  });

  it("ignores marks of students who are not on the roster", () => {
    expect(markedCount(IDS, { a: "PRESENT", zzz: "ABSENT" })).toBe(1);
  });

  it("marks everyone at once and builds entries in roster order", () => {
    const marks = markAll(IDS, "PRESENT");
    expect(allMarked(IDS, marks)).toBe(true);
    expect(toEntries(IDS, { ...marks, b: "EXCUSED" })).toEqual([
      { studentId: "a", status: "PRESENT" },
      { studentId: "b", status: "EXCUSED" },
      { studentId: "c", status: "PRESENT" },
    ]);
  });
});
