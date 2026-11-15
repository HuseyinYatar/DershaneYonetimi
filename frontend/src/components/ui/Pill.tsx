import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Link } from "react-router";
import { cn } from "@/lib/utils";

const PILL =
  "inline-flex h-8 items-center gap-1.5 rounded-full border border-line bg-white px-3 text-[13px] font-medium whitespace-nowrap hover:border-[#cfd2d9] disabled:opacity-50";

export function PillLink({
  to,
  children,
  className,
}: {
  to: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Link to={to} className={cn(PILL, className)}>
      {children}
    </Link>
  );
}

export function PillButton({
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { children: ReactNode }) {
  return <button type="button" className={cn(PILL, className)} {...props} />;
}

// Segmented toggle (subject tabs, exam-type switcher): ink fill when active.
export function SegButton({
  active,
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { active: boolean; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      className={cn(
        "h-8 rounded-full border px-3.5 text-[13px] font-medium",
        active ? "border-ink bg-ink text-white" : "border-line bg-white hover:border-[#cfd2d9]",
        className,
      )}
      {...props}
    />
  );
}
