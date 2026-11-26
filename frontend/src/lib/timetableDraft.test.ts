import { describe, expect, it } from "vitest";
import { ApiError } from "@/api/problem";
import { buildSlots, DEFAULT_TIMETABLE } from "./scheduleBoard";
import { draftToBlocks, isDraftMissing } from "./timetableDraft";

const SLOTS = buildSlots(DEFAULT_TIMETABLE);

describe("isDraftMissing", () => {
  it("is true only for the draft-not-found 404", () => {
    const missing = new ApiError(404, { errorCode: "TIMETABLE_DRAFT_NOT_FOUND", detail: "Yok" });
    expect(isDraftMissing(missing)).toBe(true);
    expect(isDraftMissing(new ApiError(404, { errorCode: "SCHEDULE_NOT_FOUND" }))).toBe(false);
    expect(isDraftMissing(new ApiError(500, { errorCode: "TIMETABLE_DRAFT_NOT_FOUND" }))).toBe(
      false,
    );
    expect(isDraftMissing(new Error("network"))).toBe(false);
  });
});

describe("draftToBlocks", () => {
  const lesson = (id: string, startsAt: string, endsAt: string) => ({
    id,
    classroomId: "c1",
    teacherId: "t1",
    subjectId: "s1",
    dayOfWeek: "MONDAY",
    startsAt,
    endsAt,
  });

  it("turns clock times into grid rows", () => {
    const [block] = draftToBlocks([lesson("a", "10:10", "12:00")], SLOTS);

    expect(block).toMatchObject({ id: "a", classroomId: "c1", span: { first: 2, last: 3 } });
  });

  it("reads seconds-terminated times and drops lessons outside the grid", () => {
    const blocks = draftToBlocks(
      [lesson("a", "08:10:00", "09:00:00"), lesson("late", "22:00", "23:00")],
      SLOTS,
    );

    expect(blocks.map((b) => b.id)).toEqual(["a"]);
  });

  it("is empty without lessons", () => {
    expect(draftToBlocks(undefined, SLOTS)).toEqual([]);
  });
});
