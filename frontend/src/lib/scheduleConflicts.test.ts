import { describe, expect, it } from "vitest";
import { type ConflictBlock, findConflicts, findLunchConflicts } from "./scheduleConflicts";

const block = (
  id: string,
  classroomId: string,
  teacherId: string,
  first: number,
  last: number,
  dayOfWeek = "MONDAY",
): ConflictBlock => ({
  id,
  classroomId,
  teacherId,
  dayOfWeek,
  span: { first, last },
});

describe("findConflicts", () => {
  it("finds a teacher in two classrooms at once, with the shared hours", () => {
    const found = findConflicts([block("a", "c1", "t1", 2, 3), block("b", "c2", "t1", 3, 4)]);

    expect(found).toHaveLength(1);
    expect(found[0]).toMatchObject({
      kind: "teacher",
      dayOfWeek: "MONDAY",
      span: { first: 3, last: 3 },
    });
  });

  it("finds two lessons of one classroom at once", () => {
    const found = findConflicts([block("a", "c1", "t1", 0, 1), block("b", "c1", "t2", 1, 1)]);

    expect(found.map((c) => c.kind)).toEqual(["classroom"]);
  });

  it("ignores neighbours, other days and unrelated lessons", () => {
    expect(
      findConflicts([
        block("a", "c1", "t1", 0, 1),
        block("b", "c2", "t1", 2, 3),
        block("c", "c2", "t1", 0, 1, "TUESDAY"),
        block("d", "c3", "t2", 0, 1),
      ]),
    ).toEqual([]);
  });
});

describe("findLunchConflicts", () => {
  it("flags every lesson that covers the lunch slot, wide boxes included", () => {
    const found = findLunchConflicts(
      [block("a", "c1", "t1", 3, 3), block("b", "c1", "t1", 2, 4), block("c", "c2", "t2", 5, 6)],
      3,
    );

    expect(found.map((c) => c.a.id)).toEqual(["a", "b"]);
    expect(found[0]).toMatchObject({ kind: "lunch", span: { first: 3, last: 3 } });
  });
});
