import { describe, expect, it } from "vitest";
import { mergeAdjacent, type Mergeable } from "./scheduleGroups";

const lesson = (
  id: string,
  first: number,
  last: number,
  over: Partial<Mergeable> = {},
): Mergeable => ({
  id,
  classroomId: "c1",
  teacherId: "t1",
  subjectId: "s1",
  dayOfWeek: "MONDAY",
  span: { first, last },
  draft: false,
  ...over,
});

describe("mergeAdjacent", () => {
  it("joins back-to-back lessons of the same teacher, subject and classroom", () => {
    const groups = mergeAdjacent([lesson("b", 2, 3), lesson("a", 0, 1), lesson("c", 4, 4)]);

    expect(groups).toHaveLength(1);
    expect(groups[0]).toMatchObject({ id: "a", ids: ["a", "b", "c"], span: { first: 0, last: 4 } });
  });

  it("keeps lessons apart when there is a gap", () => {
    expect(mergeAdjacent([lesson("a", 0, 1), lesson("b", 3, 3)])).toHaveLength(2);
  });

  it("never merges across teacher, subject, classroom, day or draft state", () => {
    const groups = mergeAdjacent([
      lesson("a", 0, 0),
      lesson("b", 1, 1, { teacherId: "t2" }),
      lesson("c", 1, 1, { subjectId: "s2" }),
      lesson("d", 1, 1, { classroomId: "c2" }),
      lesson("e", 1, 1, { dayOfWeek: "TUESDAY" }),
      lesson("f", 1, 1, { draft: true }),
    ]);

    expect(groups).toHaveLength(6);
  });
});
