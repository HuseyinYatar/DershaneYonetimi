import { cn } from "@/lib/utils";
import { dateKey, shortDayName } from "@/lib/week";

// Seven day cells, a dot under days that have deadlines. Interactive when `onSelect` is given.
export function WeekStrip({
  days,
  selected,
  marked,
  onSelect,
  label,
}: {
  days: Date[];
  selected?: string;
  marked: Set<string>;
  onSelect?: (key: string) => void;
  label: string;
}) {
  const todayKey = dateKey(new Date());
  return (
    <div role="group" aria-label={label} className="grid grid-cols-7 gap-1">
      {days.map((day) => {
        const key = dateKey(day);
        const isSelected = key === selected;
        const cell = (
          <>
            <span className="text-xs text-dim">{shortDayName(day)}</span>
            <span
              className={cn(
                "grid size-9 place-items-center rounded-[11px] text-[15px]",
                isSelected && "bg-brand-tint font-medium text-brand-ink",
                !isSelected && key === todayKey && "font-medium text-brand-ink",
              )}
            >
              {day.getDate()}
            </span>
            <i
              aria-hidden="true"
              className={cn("block size-1 rounded-full bg-ink", !marked.has(key) && "invisible")}
            />
          </>
        );
        const base = "flex flex-col items-center gap-1.5 rounded-xl py-0.5";
        return onSelect ? (
          <button
            key={key}
            type="button"
            aria-pressed={isSelected}
            aria-label={`${day.getDate()}. gün${marked.has(key) ? ", son teslim var" : ""}`}
            onClick={() => onSelect(key)}
            className={cn(base, "cursor-pointer hover:[&>span:nth-child(2)]:bg-[#f1f2f5]")}
          >
            {cell}
          </button>
        ) : (
          <div key={key} className={base}>
            {cell}
          </div>
        );
      })}
    </div>
  );
}
