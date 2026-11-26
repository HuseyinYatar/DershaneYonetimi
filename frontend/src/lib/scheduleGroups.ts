import type { SlotSpan } from "./scheduleBoard";

export interface Mergeable {
  id: string;
  classroomId: string;
  teacherId: string;
  subjectId: string;
  dayOfWeek: string;
  span: SlotSpan;
  draft: boolean;
}

// One box on the board: back-to-back lessons of the same teacher, subject and classroom, drawn as a
// single wider box. `id` is the first member's; `ids` lists every lesson it stands for.
export type Group<T extends Mergeable> = T & { ids: string[] };

// Saved lessons and drafts are never merged together (a draft can be thrown away on its own).
export function mergeAdjacent<T extends Mergeable>(blocks: T[]): Group<T>[] {
  const buckets = new Map<string, T[]>();
  for (const b of blocks) {
    const key = [b.dayOfWeek, b.classroomId, b.teacherId, b.subjectId, b.draft].join("|");
    buckets.set(key, [...(buckets.get(key) ?? []), b]);
  }
  const groups: Group<T>[] = [];
  for (const list of buckets.values()) {
    let current: Group<T> | undefined;
    for (const b of [...list].sort((x, y) => x.span.first - y.span.first)) {
      if (current && b.span.first <= current.span.last + 1) {
        current.ids.push(b.id);
        current.span = {
          first: current.span.first,
          last: Math.max(current.span.last, b.span.last),
        };
      } else {
        current = { ...b, ids: [b.id] };
        groups.push(current);
      }
    }
  }
  return groups;
}
