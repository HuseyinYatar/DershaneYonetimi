import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export const INPUT =
  "h-10 w-full rounded-2xl border border-line bg-white px-3 text-sm disabled:bg-sub disabled:text-dim";
export const TEXTAREA =
  "w-full resize-y rounded-2xl border border-line bg-white p-3 text-sm disabled:bg-sub disabled:text-dim";
export const PRIMARY_BUTTON =
  "inline-flex h-10 items-center rounded-full bg-ink px-5 text-sm font-medium text-white disabled:opacity-50";

// Label above a control, the validation message below it.
export function Field({
  label,
  htmlFor,
  error,
  className,
  children,
}: {
  label: string;
  htmlFor?: string;
  error?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <label htmlFor={htmlFor} className="text-[13px] text-dim">
        {label}
      </label>
      {children}
      {error && (
        <p role="alert" className="text-[13px] text-(--status-danger-fg)">
          {error}
        </p>
      )}
    </div>
  );
}
