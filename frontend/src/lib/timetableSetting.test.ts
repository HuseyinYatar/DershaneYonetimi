import { describe, expect, it } from "vitest";
import { DEFAULT_TIMETABLE } from "./scheduleBoard";
import {
  previewOf,
  timetableFormSchema,
  toFormValues,
  toSetting,
  type TimetableFormValues,
} from "./timetableSetting";

const valid = toFormValues(DEFAULT_TIMETABLE);
const issuePaths = (v: TimetableFormValues) => {
  const result = timetableFormSchema.safeParse(v);
  return result.success ? [] : [...new Set(result.error.issues.map((i) => String(i.path[0])))];
};

describe("timetableFormSchema", () => {
  it("accepts the default grid and round-trips to numbers", () => {
    expect(issuePaths(valid)).toEqual([]);
    expect(toSetting(valid, 0)).toEqual(DEFAULT_TIMETABLE);
  });

  it("rejects values outside the service's limits", () => {
    expect(issuePaths({ ...valid, lessonMinutes: "10" })).toEqual(["lessonMinutes"]);
    expect(issuePaths({ ...valid, breakMinutes: "61" })).toEqual(["breakMinutes"]);
    expect(issuePaths({ ...valid, lessonCount: "0" })).toEqual(["lessonCount"]);
    expect(issuePaths({ ...valid, firstLessonStart: "04:30" })).toEqual(["firstLessonStart"]);
    expect(issuePaths({ ...valid, lessonMinutes: "abc" })).toEqual(["lessonMinutes"]);
  });

  it("rejects a day that runs past 23:59", () => {
    expect(issuePaths({ ...valid, firstLessonStart: "15:00" })).toEqual(["lessonCount"]);
  });
});

describe("previewOf", () => {
  it("shows the lesson count and the day's span", () => {
    expect(previewOf(valid)).toBe("12 ders · 08:10 – 20:00");
  });

  it("shows nothing while the form is incomplete or past midnight", () => {
    expect(previewOf({ ...valid, lessonMinutes: "" })).toBeUndefined();
    expect(previewOf({ ...valid, firstLessonStart: "15:00" })).toBeUndefined();
  });
});

describe("toSetting", () => {
  it("carries the lunch lesson along, and drops one beyond the new last lesson", () => {
    expect(toSetting(valid, 5).lunchLesson).toBe(5);
    expect(toSetting({ ...valid, lessonCount: "4" }, 5).lunchLesson).toBe(0);
  });
});
