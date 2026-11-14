import type { ReactNode } from "react";
import { Link } from "react-router";
import { ChevronRight } from "lucide-react";
import { HeaderActions } from "@/components/layout/HeaderActions";

export function Breadcrumb({
  parent,
  current,
}: {
  parent: { label: string; to: string };
  current: string;
}) {
  return (
    <nav aria-label="Konum" className="flex min-w-0 items-center gap-1.5 text-sm text-dim">
      <Link to={parent.to} className="hover:text-ink">
        {parent.label}
      </Link>
      <ChevronRight size={16} strokeWidth={1.5} aria-hidden="true" />
      <span className="truncate font-medium text-ink">{current}</span>
    </nav>
  );
}

// Small grey line, big regular-weight title, round icon buttons on the right (md and up; phones
// have them in the shell's top bar). With `breadcrumb` the icons sit on the breadcrumb row.
export function PageHeader({
  eyebrow,
  title,
  breadcrumb,
}: {
  eyebrow?: ReactNode;
  title: ReactNode;
  breadcrumb?: ReactNode;
}) {
  const heading = (
    <div className="min-w-0">
      {eyebrow && <p className="text-sm text-dim">{eyebrow}</p>}
      <h1 className="mt-1.5 text-[32px] leading-[1.1] font-normal tracking-[-0.035em] md:text-[40px]">
        {title}
      </h1>
    </div>
  );
  const actions = (
    <div className="hidden md:block">
      <HeaderActions />
    </div>
  );
  if (breadcrumb) {
    return (
      <header>
        <div className="flex min-h-10 items-center justify-between gap-4">
          {breadcrumb}
          {actions}
        </div>
        <div className="mt-6">{heading}</div>
      </header>
    );
  }
  return (
    <header className="flex items-start justify-between gap-4">
      {heading}
      {actions}
    </header>
  );
}
