import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

// White surface, 1px border, no shadow.
export function Card({
  className,
  children,
  as: Tag = "section",
  id,
}: {
  className?: string;
  children: ReactNode;
  as?: "section" | "div" | "li";
  id?: string;
}) {
  return (
    <Tag id={id} className={cn("rounded-[20px] border border-line bg-white", className)}>
      {children}
    </Tag>
  );
}

export function CardTitle({ children, icon }: { children: ReactNode; icon?: ReactNode }) {
  return (
    <h2 className="flex items-center gap-2.5 text-lg leading-tight font-medium tracking-[-0.01em]">
      {icon}
      {children}
    </h2>
  );
}

export function CardHeader({
  title,
  icon,
  action,
  className,
}: {
  title: ReactNode;
  icon?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex items-center justify-between gap-3", className)}>
      <CardTitle icon={icon}>{title}</CardTitle>
      {action}
    </div>
  );
}
