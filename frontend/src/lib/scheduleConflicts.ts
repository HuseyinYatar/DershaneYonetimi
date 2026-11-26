import type { SlotSpan } from "./scheduleBoard";

export interface ConflictBlock {
  id: string;
  classroomId: string;
  teacherId: string;
  dayOfWeek: string;
  span: SlotSpan;
}

// Two lessons that claim the same teacher or the same classroom at the same time (`span` is the shared
// stretch), or one lesson on the lunch break (`a` and `b` are that lesson, `span` is the lunch slot).
export interface Conflict<T extends ConflictBlock = ConflictBlock> {
  kind: "teacher" | "classroom" | "lunch";
  dayOfWeek: string;
  span: SlotSpan;
  a: T;
  b: T;
}

// shortcut: compares every pair (O(n²)); a branch has a few hundred lessons, bucket by day if that grows.
export function findConflicts<T extends ConflictBlock>(blocks: T[]): Conflict<T>[] {
  const found: Conflict<T>[] = [];
  blocks.forEach((a, i) => {
    for (const b of blocks.slice(i + 1)) {
      if (a.dayOfWeek !== b.dayOfWeek || a.span.first > b.span.last || b.span.first > a.span.last) {
        continue;
      }
      const span = {
        first: Math.max(a.span.first, b.span.first),
        last: Math.min(a.span.last, b.span.last),
      };
      if (a.teacherId === b.teacherId)
        found.push({ kind: "teacher", dayOfWeek: a.dayOfWeek, span, a, b });
      if (a.classroomId === b.classroomId) {
        found.push({ kind: "classroom", dayOfWeek: a.dayOfWeek, span, a, b });
      }
    }
  });
  return found;
}

// Lessons that sit on the lunch slot (the whole box counts when it is wider than one hour).
export function findLunchConflicts<T extends ConflictBlock>(
  blocks: T[],
  lunchSlot: number,
): Conflict<T>[] {
  return blocks
    .filter((b) => b.span.first <= lunchSlot && lunchSlot <= b.span.last)
    .map((b) => ({
      kind: "lunch",
      dayOfWeek: b.dayOfWeek,
      span: { first: lunchSlot, last: lunchSlot },
      a: b,
      b,
    }));
}
