import { Utensils, X } from "lucide-react";
import type { Slot, SlotSpan } from "@/lib/scheduleBoard";
import { DAY_LABELS } from "@/lib/scheduleForm";
import { assignLanes } from "@/lib/scheduleLanes";
import type { Weekday } from "@/lib/taskForm";
import { cn } from "@/lib/utils";

export interface GridBlock {
  id: string;
  ids: string[];
  classroomId: string;
  teacherId: string;
  subjectId: string;
  dayOfWeek: string;
  span: SlotSpan;
  draft: boolean;
}

// The whole branch's week in one table: a row per day, a column per lesson hour. Every classroom's lesson
// is a box (subject, teacher, classroom); lessons of the same hour stack inside the day's row.
// Boxes drag to another day/hour; drops on empty cells are handled by the board.
export function WeekOverviewGrid({
  slots,
  lunchSlot,
  lunchDragged,
  onLunchDragStart,
  onLunchRemove,
  days,
  blocks,
  classroomNames,
  teacherNames,
  subjectNames,
  conflictIds,
  dragging,
  draggedId,
  tone,
  onOver,
  onDrop,
  onBlockDragStart,
  onDragEnd,
  onRemove,
  onOpen,
}: {
  slots: Slot[];
  /** Slot index of the lunch break (taken on every day), if one is set. */
  lunchSlot?: number;
  lunchDragged: boolean;
  onLunchDragStart: (e: React.DragEvent) => void;
  onLunchRemove: () => void;
  days: { value: Weekday }[];
  blocks: GridBlock[];
  classroomNames: Map<string | undefined, string | undefined>;
  teacherNames: Map<string | undefined, string>;
  subjectNames: Map<string, string> | undefined;
  conflictIds: Set<string>;
  dragging: boolean;
  draggedId?: string;
  tone: (day: string, slot: number) => string | undefined;
  onOver: (key: string) => void;
  onDrop: (day: Weekday, slot: number) => void;
  onBlockDragStart: (id: string) => (e: React.DragEvent) => void;
  onDragEnd: () => void;
  onRemove: (block: GridBlock) => void;
  onOpen: (classroomId: string) => void;
}) {
  const perDay = days.map((d) => {
    const dayBlocks = blocks.filter((b) => b.dayOfWeek === d.value);
    const { lanes, laneOf } = assignLanes(dayBlocks);
    return { day: d.value, count: Math.max(1, lanes), dayBlocks, laneOf };
  });
  // Row 1 is the header; each day takes as many rows as it has lanes.
  const layout = perDay.map((day, i) => ({
    ...day,
    start: 2 + perDay.slice(0, i).reduce((n, x) => n + x.count, 0),
  }));
  const dayRows = perDay.reduce((n, x) => n + x.count, 0);
  return (
    <div className="mt-4 overflow-x-auto">
      <div
        className="grid border border-line"
        style={{
          minWidth: 88 + slots.length * 120,
          gridTemplateColumns: `88px repeat(${slots.length}, minmax(120px, 1fr))`,
          gridTemplateRows: `auto repeat(${dayRows}, minmax(64px, auto))`,
        }}
      >
        <div className="border-b border-line" />
        {slots.map((slot, i) => (
          <div
            key={slot.startsAt}
            style={{ gridRow: 1, gridColumn: i + 2 }}
            className="border-b border-l border-line px-2 py-1.5 text-center text-[12px]"
          >
            <div className="font-medium">{i + 1}</div>
            <div className="text-dim">
              {slot.startsAt} {slot.endsAt}
            </div>
          </div>
        ))}
        {layout.map(({ day, start, count }) => (
          <div
            key={day}
            style={{ gridRow: `${start} / span ${count}`, gridColumn: 1 }}
            className="flex items-center border-b border-line px-2 text-[13px] font-medium"
          >
            {DAY_LABELS[day]}
          </div>
        ))}
        {layout.flatMap(({ day, start, count }) =>
          slots.map((_, i) => {
            const key = `${day}-${i}`;
            return (
              <div
                key={key}
                style={{ gridRow: `${start} / span ${count}`, gridColumn: i + 2 }}
                onDragOver={(e) => {
                  if (!dragging) return;
                  e.preventDefault();
                  onOver(key);
                }}
                onDragLeave={() => onOver("")}
                onDrop={(e) => {
                  e.preventDefault();
                  onDrop(day, i);
                }}
                className={cn("border-b border-l border-line transition-colors", tone(day, i))}
              />
            );
          }),
        )}
        {layout.flatMap(({ start, dayBlocks, laneOf }) =>
          dayBlocks.map((b) => {
            const conflict = b.ids.some((id) => conflictIds.has(id));
            const subject = subjectNames?.get(b.subjectId) ?? "Ders";
            const teacher = teacherNames.get(b.teacherId) ?? "Öğretmen";
            const classroom = classroomNames.get(b.classroomId) ?? "Sınıf";
            return (
              <div
                key={b.id}
                draggable
                onDragStart={onBlockDragStart(b.id)}
                onDragEnd={onDragEnd}
                title={conflict ? "Çakışma var" : undefined}
                style={{
                  gridRow: start + (laneOf.get(b.id) ?? 0),
                  gridColumn: `${b.span.first + 2} / span ${b.span.last - b.span.first + 1}`,
                }}
                className={cn(
                  "anim-fade relative m-0.5 min-w-0 cursor-grab rounded-lg border p-1.5 pr-6 text-center text-[12px] leading-tight transition-opacity active:cursor-grabbing",
                  draggedId === b.id && "opacity-40",
                  b.draft ? "border-dashed border-ink/40 bg-white" : "border-line bg-brand-tint",
                  conflict && "border-(--status-danger-fg) bg-(--status-danger-bg)",
                  dragging && "pointer-events-none",
                )}
              >
                <div className="truncate font-medium">{subject}</div>
                <div className="truncate text-dim">{teacher}</div>
                <button
                  type="button"
                  aria-label={`${classroom} sınıfını aç`}
                  onClick={() => onOpen(b.classroomId)}
                  className="mt-0.5 max-w-full truncate rounded-full bg-white/80 px-2 text-[11px] font-medium hover:bg-white"
                >
                  {classroom}
                </button>
                <button
                  type="button"
                  aria-label={b.draft ? "Taslak dersi kaldır" : "Dersi programdan kaldır"}
                  onClick={() => onRemove(b)}
                  className="absolute top-1 right-1 grid size-5 place-items-center rounded-full hover:bg-sub"
                >
                  <X size={12} strokeWidth={1.75} aria-hidden="true" />
                </button>
              </div>
            );
          }),
        )}
        {lunchSlot !== undefined &&
          layout.map(({ day, start, count }) => (
            <div
              key={`lunch-${day}`}
              draggable
              onDragStart={onLunchDragStart}
              onDragEnd={onDragEnd}
              style={{ gridRow: `${start} / span ${count}`, gridColumn: lunchSlot + 2 }}
              className={cn(
                "anim-fade relative m-0.5 flex min-w-0 cursor-grab flex-col items-center justify-center gap-1 rounded-lg border border-line bg-sub p-1.5 text-[12px] font-medium text-dim transition-opacity active:cursor-grabbing",
                dragging && "pointer-events-none",
                lunchDragged && "opacity-40",
              )}
            >
              <Utensils size={14} strokeWidth={1.5} aria-hidden="true" />
              Öğle arası
              <button
                type="button"
                aria-label="Öğle arasını kaldır"
                onClick={onLunchRemove}
                className="absolute top-1 right-1 grid size-5 place-items-center rounded-full hover:bg-white"
              >
                <X size={12} strokeWidth={1.75} aria-hidden="true" />
              </button>
            </div>
          ))}
      </div>
    </div>
  );
}
