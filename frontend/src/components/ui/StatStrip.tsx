import type { LucideIcon } from "lucide-react";
import { Link } from "react-router";
import { cn } from "@/lib/utils";
import { Skeleton } from "./Skeleton";

export interface Stat {
  icon: LucideIcon;
  value: string;
  label: string;
  // Makes the cell a link.
  to?: string;
}

const STRIP =
  "flex w-full items-stretch rounded-[20px] border border-line bg-white px-1 py-1.5 sm:inline-flex sm:w-auto sm:rounded-full sm:px-2";

// Same pill with a grey icon, number and label per cell.
export function StatStripSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className={STRIP}>
      {Array.from({ length: count }, (_, i) => (
        <div
          key={i}
          className="flex flex-1 items-center gap-2 px-3 py-2 not-first:border-l not-first:border-line sm:px-[22px] sm:py-1.5"
        >
          <Skeleton className="size-4 shrink-0 rounded-full" />
          <Skeleton className="h-4 w-7 rounded-full sm:w-9" />
          <Skeleton className="hidden h-3.5 w-9 rounded-full sm:block" />
        </div>
      ))}
    </div>
  );
}

// Bordered pill with inline stats; on phones it becomes a full-width row of stacked stats.
export function StatStrip({ stats }: { stats: Stat[] }) {
  return (
    <div className={STRIP}>
      {stats.map(({ icon: Icon, value, label, to }) => {
        const className = cn(
          // The divider is a pseudo-element, not a border: a border would curve with the rounded hover.
          "relative grid flex-1 grid-cols-[auto_1fr] items-center gap-x-2 gap-y-0.5 px-3 py-2 text-[13px] text-dim not-first:after:absolute not-first:after:inset-y-2 not-first:after:left-0 not-first:after:w-px not-first:after:bg-line sm:flex sm:gap-2 sm:px-[22px] sm:py-1.5 sm:text-sm",
          to && "rounded-2xl transition-colors hover:bg-sub hover:after:opacity-0 sm:rounded-full",
        );
        const content = (
          <>
            <Icon aria-hidden="true" size={16} strokeWidth={1.5} className="text-[#3a3f4a]" />
            <b className="text-[17px] font-medium tracking-[-0.02em] text-ink sm:text-base">
              {value}
            </b>
            <span className="col-span-2 leading-tight sm:col-span-1 sm:whitespace-nowrap">
              {label}
            </span>
          </>
        );
        return to ? (
          <Link key={label} to={to} className={className}>
            {content}
          </Link>
        ) : (
          <div key={label} className={className}>
            {content}
          </div>
        );
      })}
    </div>
  );
}
