import type { SlotSpan } from "./scheduleBoard";

// Stacks lessons that share hours into lanes (lane 0 on top), so every classroom's lesson of the same
// hour gets its own row inside the day. Greedy interval partitioning: a lesson takes the first lane
// that is free by the time it starts.
export function assignLanes<T extends { id: string; span: SlotSpan }>(
  blocks: T[],
): { lanes: number; laneOf: Map<string, number> } {
  const laneEnds: number[] = [];
  const laneOf = new Map<string, number>();
  const ordered = [...blocks].sort(
    (a, b) => a.span.first - b.span.first || b.span.last - a.span.last || a.id.localeCompare(b.id),
  );
  for (const block of ordered) {
    let lane = laneEnds.findIndex((end) => end < block.span.first);
    if (lane === -1) lane = laneEnds.length;
    laneEnds[lane] = block.span.last;
    laneOf.set(block.id, lane);
  }
  return { lanes: laneEnds.length, laneOf };
}
