import { Check, ChevronDown } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { cn } from "@/lib/utils";

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectProps {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  loading?: boolean;
  disabled?: boolean;
  invalid?: boolean;
  "aria-label"?: string;
  className?: string;
}

// Dropdown in the app's own look (same popover language as MonthPicker) instead of the browser's.
// Keyboard: Arrow keys / Home / End move, Enter or Space picks, Escape closes.
// shortcut: no type-ahead search; add it if a list gets long enough to need it.
export function Select({
  id,
  value,
  onChange,
  options,
  placeholder = "Seçin",
  loading = false,
  disabled = false,
  invalid = false,
  "aria-label": ariaLabel,
  className,
}: SelectProps) {
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const list = useRef<HTMLUListElement>(null);
  const listId = useId();
  const selected = options.find((o) => o.value === value);

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointer);
    return () => document.removeEventListener("pointerdown", onPointer);
  }, [open]);

  useEffect(() => {
    if (open) list.current?.focus();
  }, [open]);

  useEffect(() => {
    if (open) {
      list.current?.children[active]?.scrollIntoView?.({ block: "nearest" });
    }
  }, [open, active]);

  const openList = () => {
    setActive(
      Math.max(
        0,
        options.findIndex((o) => o.value === value),
      ),
    );
    setOpen(true);
  };
  const close = () => {
    setOpen(false);
    trigger.current?.focus();
  };
  const pick = (option: SelectOption) => {
    onChange(option.value);
    close();
  };

  const onTriggerKey = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      openList();
    }
  };

  const onListKey = (e: React.KeyboardEvent) => {
    const last = options.length - 1;
    if (e.key === "ArrowDown") setActive((i) => Math.min(i + 1, last));
    else if (e.key === "ArrowUp") setActive((i) => Math.max(i - 1, 0));
    else if (e.key === "Home") setActive(0);
    else if (e.key === "End") setActive(last);
    else if (e.key === "Enter" || e.key === " ") {
      if (options[active]) pick(options[active]);
    } else if (e.key === "Escape") close();
    else if (e.key === "Tab") setOpen(false);
    else return;
    e.preventDefault();
  };

  return (
    <div ref={root} className={cn("relative", className)}>
      <button
        ref={trigger}
        id={id}
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-label={ariaLabel}
        aria-invalid={invalid || undefined}
        disabled={disabled}
        onClick={() => (open ? close() : openList())}
        onKeyDown={onTriggerKey}
        className={cn(
          "flex h-10 w-full items-center justify-between gap-2 rounded-2xl border bg-white px-3 text-left text-sm hover:border-[#cfd2d9] disabled:bg-sub disabled:text-dim",
          invalid ? "border-(--status-danger-fg)" : "border-line",
        )}
      >
        <span className={cn("truncate", !selected && "text-dim")}>
          {loading ? "Yükleniyor…" : (selected?.label ?? placeholder)}
        </span>
        <ChevronDown
          className={cn("size-4 shrink-0 text-dim transition-transform", open && "rotate-180")}
          aria-hidden="true"
        />
      </button>

      {open && (
        <ul
          ref={list}
          id={listId}
          role="listbox"
          tabIndex={-1}
          aria-label={ariaLabel}
          aria-activedescendant={options[active] ? `${listId}-${active}` : undefined}
          onKeyDown={onListKey}
          className="absolute left-0 z-20 mt-2 max-h-64 w-full min-w-40 overflow-auto rounded-2xl border border-line bg-white p-1 outline-none"
        >
          {options.length === 0 && <li className="px-3 py-2 text-sm text-dim">Seçenek yok</li>}
          {options.map((o, i) => (
            <li
              key={o.value}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={o.value === value}
              onPointerEnter={() => setActive(i)}
              onClick={() => pick(o)}
              className={cn(
                "flex h-9 cursor-pointer items-center justify-between gap-2 rounded-xl px-3 text-sm",
                i === active && "bg-sub",
                o.value === value && "bg-brand-tint font-medium text-brand-ink",
              )}
            >
              <span className="truncate">{o.label}</span>
              {o.value === value && <Check className="size-4 shrink-0" aria-hidden="true" />}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
