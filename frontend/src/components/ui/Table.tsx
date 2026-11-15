import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

// Grid-based table: `cols` is a full Tailwind class such as "grid-cols-[minmax(0,1fr)_130px]"
// (written out by the caller so Tailwind can see it).
export function TableHead({ cols, children }: { cols: string; children: ReactNode }) {
  return (
    <div role="row" className={cn("grid border-t border-line bg-surface px-5", cols)}>
      {children}
    </div>
  );
}

export function Th({
  icon: Icon,
  children,
  className,
  divider = false,
}: {
  icon?: LucideIcon;
  children?: ReactNode;
  className?: string;
  divider?: boolean;
}) {
  return (
    <div
      role="columnheader"
      className={cn(
        "flex items-center gap-2 py-[11px] text-[13px] font-medium text-[#3a3f4a]",
        divider && "border-l border-line pl-4",
        className,
      )}
    >
      {Icon && <Icon aria-hidden="true" size={16} strokeWidth={1.5} className="text-[#4b5160]" />}
      {children}
    </div>
  );
}

export function TableRow({
  cols,
  children,
  className,
}: {
  cols: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      role="row"
      className={cn(
        "grid items-center border-t border-line px-5 py-4 tabular-nums",
        cols,
        className,
      )}
    >
      {children}
    </div>
  );
}

export function Td({
  children,
  className,
  divider = false,
}: {
  children?: ReactNode;
  className?: string;
  divider?: boolean;
}) {
  return (
    <div role="cell" className={cn("min-w-0", divider && "pl-4", className)}>
      {children}
    </div>
  );
}
