// Time grid of the drag-and-drop weekly timetable. The branch sets it (first lesson, lesson and break
// length, lessons per day); a schedule that falls outside the grid is not shown on the board.
export interface TimetableSetting {
  firstLessonStart: string; // "HH:mm"
  lessonMinutes: number;
  breakMinutes: number;
  lessonCount: number;
  // The lesson (1-based) that is the lunch break: that hour is taken on every day and for every
  // classroom. 0 = no lunch break.
  lunchLesson: number;
}

// What the service answers for a branch that never customized it.
export const DEFAULT_TIMETABLE: TimetableSetting = {
  firstLessonStart: "08:10",
  lessonMinutes: 50,
  breakMinutes: 10,
  lessonCount: 12,
  lunchLesson: 0,
};

export const clock = (minutes: number) =>
  `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;

export const toMinutes = (hhmm: string) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));

export interface Slot {
  startsAt: string;
  endsAt: string;
}

export function buildSlots(setting: TimetableSetting): Slot[] {
  const first = toMinutes(setting.firstLessonStart);
  return Array.from({ length: setting.lessonCount }, (_, i) => {
    const start = first + i * (setting.lessonMinutes + setting.breakMinutes);
    return { startsAt: clock(start), endsAt: clock(start + setting.lessonMinutes) };
  });
}

export interface SlotSpan {
  first: number;
  last: number;
}

// Slots a "HH:mm" time range touches; a range that falls outside the grid has no span.
export function spanOf(slots: Slot[], startsAt: string, endsAt: string): SlotSpan | undefined {
  const start = toMinutes(startsAt);
  const end = toMinutes(endsAt);
  const first = slots.findIndex((s) => toMinutes(s.endsAt) > start);
  // The project's TS lib has no findLastIndex: the last slot that starts before the range ends.
  const last = slots.reduce((found, s, i) => (toMinutes(s.startsAt) < end ? i : found), -1);
  return first === -1 || last < first ? undefined : { first, last };
}

export function timesOf(slots: Slot[], { first, last }: SlotSpan): Slot {
  return { startsAt: slots[first].startsAt, endsAt: slots[last].endsAt };
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
  ignoreId?: string | string[],
): boolean {
  return placed.some(
    (p) =>
      !(Array.isArray(ignoreId) ? ignoreId.includes(p.id) : p.id === ignoreId) &&
      p.dayOfWeek === day &&
      p.span.first <= span.last &&
      span.first <= p.span.last,
  );
}
