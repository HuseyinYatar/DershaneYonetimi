import { Check, ChevronDown, Filter } from "lucide-react";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { StatusBadge } from "@/components/StatusBadge";
import { assignmentBadge, type AssignmentStatus } from "@/lib/status/tasks";
import { cn } from "@/lib/utils";

// Status filter pill with a dropdown list (same popover pattern as MonthPicker).
export function StatusFilter({
  statuses,
  value,
  onChange,
}: {
  statuses: readonly AssignmentStatus[];
  value: AssignmentStatus | undefined;
  onChange: (value: AssignmentStatus | undefined) => void;
}) {
  const [open, setOpen] = useState(false);
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

  const select = (next: AssignmentStatus | undefined) => {
    onChange(next);
    setOpen(false);
  };

  return (
    <div ref={root} className="relative inline-block">
      <button
        type="button"
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen(!open)}
        className="inline-flex h-8 items-center gap-1.5 rounded-full border border-line bg-white pr-2.5 pl-3 text-[13px] font-medium hover:border-[#cfd2d9]"
      >
        <Filter size={14} strokeWidth={1.5} aria-hidden="true" />
        <span className="text-dim">Durum</span>
        <span>{value ? assignmentBadge(value).label : "Tümü"}</span>
        <ChevronDown
          size={14}
          strokeWidth={1.5}
          aria-hidden="true"
          className={cn(open && "rotate-180")}
        />
      </button>

      {open && (
        <div
          id={panelId}
          role="group"
          aria-label="Duruma göre süz"
          className="absolute left-0 z-20 mt-1.5 flex w-[200px] flex-col rounded-2xl border border-line bg-white p-1.5"
        >
          <Option selected={value === undefined} onClick={() => select(undefined)}>
            Tümü
          </Option>
          {statuses.map((s) => {
            const badge = assignmentBadge(s);
            return (
              <Option key={s} selected={value === s} onClick={() => select(s)}>
                <StatusBadge tone={badge.tone} label={badge.label} size="sm" />
              </Option>
            );
          })}
        </div>
      )}
    </div>
  );
}

function Option({
  selected,
  onClick,
  children,
}: {
  selected: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={cn(
        "flex h-9 items-center justify-between rounded-xl px-2.5 text-[13px] hover:bg-sub",
        selected && "bg-brand-tint font-medium text-brand-ink hover:bg-brand-tint",
      )}
    >
      <span className="flex items-center">{children}</span>
      <Check
        size={14}
        strokeWidth={1.75}
        aria-hidden="true"
        className={selected ? "" : "invisible"}
      />
    </button>
  );
}
