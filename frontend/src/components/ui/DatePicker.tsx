import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { parseDateOnly } from "@/lib/format";
import { dateKey } from "@/lib/week";
import { cn } from "@/lib/utils";

const WEEKDAY_HEADS = ["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"];
const monthTitle = new Intl.DateTimeFormat("tr-TR", { month: "long", year: "numeric" });
const fullDate = new Intl.DateTimeFormat("tr-TR", {
  day: "numeric",
  month: "long",
  year: "numeric",
});

// Same day-of-month in another month, clamped (31 Oct + 1 month = 30 Nov).
function shiftMonth(date: Date, months: number): Date {
  const target = new Date(date.getFullYear(), date.getMonth() + months, 1);
  const daysInMonth = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
  target.setDate(Math.min(date.getDate(), daysInMonth));
  return target;
}

const addDays = (date: Date, days: number) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);

export interface DatePickerProps {
  id?: string;
  /** "YYYY-MM-DD", or "" for no date. */
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  /** Offers "Temizle" for optional dates. */
  clearable?: boolean;
  disabled?: boolean;
  invalid?: boolean;
  "aria-label"?: string;
  className?: string;
}

// Date field in the app's own look (month popover like MonthPicker) instead of the browser's.
// Keyboard: arrows move by day/week, PageUp/PageDown by month, Enter or Space picks, Escape closes.
export function DatePicker({
  id,
  value,
  onChange,
  placeholder = "Tarih seç",
  clearable = false,
  disabled = false,
  invalid = false,
  "aria-label": ariaLabel,
  className,
}: DatePickerProps) {
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(() => new Date());
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const panelId = useId();
  const selected = value ? parseDateOnly(value) : undefined;
  const todayKey = dateKey(new Date());

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointer);
    return () => document.removeEventListener("pointerdown", onPointer);
  }, [open]);

  // Keyboard focus follows the active day.
  useEffect(() => {
    if (open) {
      panel.current?.querySelector<HTMLElement>(`[data-date="${dateKey(active)}"]`)?.focus();
    }
  }, [open, active]);

  const close = () => {
    setOpen(false);
    trigger.current?.focus();
  };
  const pick = (next: string) => {
    onChange(next);
    close();
  };

  const first = new Date(active.getFullYear(), active.getMonth(), 1);
  const offset = (first.getDay() + 6) % 7; // weeks start on Monday
  const cells = Array.from(
    { length: 42 },
    (_, i) => new Date(first.getFullYear(), first.getMonth(), 1 - offset + i),
  );

  const onGridKey = (e: React.KeyboardEvent) => {
    const moves: Record<string, Date> = {
      ArrowLeft: addDays(active, -1),
      ArrowRight: addDays(active, 1),
      ArrowUp: addDays(active, -7),
      ArrowDown: addDays(active, 7),
      PageUp: shiftMonth(active, -1),
      PageDown: shiftMonth(active, 1),
    };
    if (e.key === "Escape") {
      e.preventDefault();
      close();
    } else if (moves[e.key]) {
      e.preventDefault();
      setActive(moves[e.key]);
    }
  };

  return (
    <div ref={root} className={cn("relative", className)}>
      <button
        ref={trigger}
        id={id}
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={ariaLabel}
        aria-invalid={invalid || undefined}
        disabled={disabled}
        onClick={() => {
          if (open) {
            close();
          } else {
            setActive(selected ?? new Date());
            setOpen(true);
          }
        }}
        className={cn(
          "flex h-10 w-full items-center gap-2 rounded-2xl border bg-white px-3 text-left text-sm hover:border-[#cfd2d9] disabled:bg-sub disabled:text-dim",
          invalid ? "border-(--status-danger-fg)" : "border-line",
        )}
      >
        <CalendarDays className="size-4 shrink-0 text-dim" aria-hidden="true" />
        <span className={cn("truncate", !selected && "text-dim")}>
          {selected ? fullDate.format(selected) : placeholder}
        </span>
      </button>

      {open && (
        <div
          ref={panel}
          id={panelId}
          role="dialog"
          aria-label="Tarih seç"
          onKeyDown={onGridKey}
          className="absolute left-0 z-20 mt-2 w-[300px] rounded-3xl border border-line bg-white p-4"
        >
          <div className="flex items-center justify-between">
            <button
              type="button"
              aria-label="Önceki ay"
              onClick={() => setActive(shiftMonth(active, -1))}
              className="grid size-8 place-items-center rounded-full hover:bg-sub"
            >
              <ChevronLeft className="size-4" aria-hidden="true" />
            </button>
            <span className="text-sm font-medium capitalize" aria-live="polite">
              {monthTitle.format(active)}
            </span>
            <button
              type="button"
              aria-label="Sonraki ay"
              onClick={() => setActive(shiftMonth(active, 1))}
              className="grid size-8 place-items-center rounded-full hover:bg-sub"
            >
              <ChevronRight className="size-4" aria-hidden="true" />
            </button>
          </div>
          <div className="mt-3 grid grid-cols-7 gap-y-1 text-center text-[12px] text-dim">
            {WEEKDAY_HEADS.map((d) => (
              <span key={d}>{d}</span>
            ))}
          </div>
          <div className="mt-1 grid grid-cols-7 gap-y-1">
            {cells.map((day) => {
              const key = dateKey(day);
              const isActive = key === dateKey(active);
              const isSelected = !!selected && key === dateKey(selected);
              return (
                <button
                  key={key}
                  type="button"
                  data-date={key}
                  tabIndex={isActive ? 0 : -1}
                  aria-label={fullDate.format(day)}
                  aria-pressed={isSelected}
                  onClick={() => pick(key)}
                  className={cn(
                    "mx-auto grid size-9 place-items-center rounded-full text-sm hover:bg-sub",
                    day.getMonth() !== active.getMonth() && "text-dim/60",
                    key === todayKey && "font-medium text-brand-ink",
                    isSelected && "bg-brand-tint font-medium text-brand-ink hover:bg-brand-tint",
                  )}
                >
                  {day.getDate()}
                </button>
              );
            })}
          </div>
          <div className="mt-3 flex justify-between border-t border-line pt-3 text-[13px]">
            <button
              type="button"
              onClick={() => pick(todayKey)}
              className="font-medium hover:underline"
            >
              Bugün
            </button>
            {clearable && value && (
              <button type="button" onClick={() => pick("")} className="text-dim hover:underline">
                Temizle
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
