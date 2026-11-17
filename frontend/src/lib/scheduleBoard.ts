// Time grid of the drag-and-drop weekly timetable: 50-minute lessons, 10-minute breaks.
// shortcut: the grid is fixed (08:10-20:00); a schedule outside it is not shown, widen SLOT_COUNT if needed.
const FIRST_START_MINUTES = 8 * 60 + 10;
const LESSON = 50;
const STEP = 60;
export const SLOT_COUNT = 12;

const clock = (minutes: number) =>
  `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;

const toMinutes = (hhmm: string) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));

export const SLOTS = Array.from({ length: SLOT_COUNT }, (_, i) => {
  const start = FIRST_START_MINUTES + i * STEP;
  return { startsAt: clock(start), endsAt: clock(start + LESSON) };
});

export interface SlotSpan {
  first: number;
  last: number;
}

// Slots a "HH:mm" time range touches; a range that falls outside the grid has no span.
export function spanOf(startsAt: string, endsAt: string): SlotSpan | undefined {
  const start = toMinutes(startsAt);
  const end = toMinutes(endsAt);
  const first = SLOTS.findIndex((s) => toMinutes(s.endsAt) > start);
  const last = SLOTS.findLastIndex((s) => toMinutes(s.startsAt) < end);
  return first === -1 || last < first ? undefined : { first, last };
}

export function timesOf({ first, last }: SlotSpan): { startsAt: string; endsAt: string } {
  return { startsAt: SLOTS[first].startsAt, endsAt: SLOTS[last].endsAt };
}

export interface Placed {
  id: string;
  dayOfWeek: string;
  span: SlotSpan;
}

// True when `span` on `day` overlaps another lesson of the same classroom (`ignoreId` is the one being moved).
export function overlaps(
  placed: Placed[],
  day: string,
  span: SlotSpan,
  ignoreId?: string,
): boolean {
  return placed.some(
    (p) =>
      p.id !== ignoreId &&
      p.dayOfWeek === day &&
      p.span.first <= span.last &&
      span.first <= p.span.last,
  );
}
