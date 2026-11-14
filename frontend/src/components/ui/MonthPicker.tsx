import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { formatMonthYear, parseDateOnly } from "@/lib/format";
import { cn } from "@/lib/utils";

const monthName = new Intl.DateTimeFormat("tr-TR", { month: "short" });
const MONTHS = Array.from({ length: 12 }, (_, i) => monthName.format(new Date(2000, i, 1)));
const pad = (n: number) => String(n).padStart(2, "0");

// Month filter pill with a year + 12-month popover. `value` is "YYYY-MM" or undefined (no filter).
export function MonthPicker({
  value,
  onChange,
}: {
  value: string | undefined;
  onChange: (value: string) => void;
}) {
  const now = new Date();
  const todayKey = `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
  const [open, setOpen] = useState(false);
  const [year, setYear] = useState(Number((value ?? todayKey).slice(0, 4)));
  const root = useRef<HTMLDivElement>(null);
  const panelId = useId();

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const toggle = () => {
    if (!open) setYear(Number((value ?? todayKey).slice(0, 4)));
    setOpen(!open);
  };

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={toggle}
        className="inline-flex h-10 items-center gap-2 rounded-full border border-line bg-white px-4 text-sm font-medium hover:border-[#cfd2d9]"
      >
        <CalendarDays className="size-4 text-dim" aria-hidden="true" />
        <span className="text-dim">Ay</span>
        <span>{value ? formatMonthYear(parseDateOnly(`${value}-01`)) : "Tümü"}</span>
      </button>

      {open && (
        <div
          id={panelId}
          role="dialog"
          aria-label="Ay seç"
          className="absolute left-0 z-20 mt-2 w-[280px] rounded-3xl border border-line bg-white p-4"
        >
          <div className="flex items-center justify-between">
            <button
              type="button"
              aria-label="Önceki yıl"
              onClick={() => setYear(year - 1)}
              className="grid size-8 place-items-center rounded-full hover:bg-sub"
            >
              <ChevronLeft className="size-4" aria-hidden="true" />
            </button>
            <span className="text-sm font-medium">{year}</span>
            <button
              type="button"
              aria-label="Sonraki yıl"
              onClick={() => setYear(year + 1)}
              className="grid size-8 place-items-center rounded-full hover:bg-sub"
            >
              <ChevronRight className="size-4" aria-hidden="true" />
            </button>
          </div>
          <div className="mt-3 grid grid-cols-3 gap-1.5">
            {MONTHS.map((name, i) => {
              const key = `${year}-${pad(i + 1)}`;
              const selected = key === value;
              return (
                <button
                  key={key}
                  type="button"
                  aria-pressed={selected}
                  onClick={() => {
                    onChange(key);
                    setOpen(false);
                  }}
                  className={cn(
                    "h-10 rounded-xl text-sm hover:bg-sub",
                    key === todayKey && "font-medium text-brand-ink",
                    selected && "bg-brand-tint font-medium text-brand-ink hover:bg-brand-tint",
                  )}
                >
                  {name}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
