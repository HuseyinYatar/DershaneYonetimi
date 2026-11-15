import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { ApiError } from "@/api/problem";
import { cn } from "@/lib/utils";

// Plain text by default; with `icon` it becomes the richer variant (icon disc, title, text, action).
export function EmptyState({
  text,
  title,
  icon: Icon,
  action,
  className,
}: {
  text: string;
  title?: string;
  icon?: LucideIcon;
  action?: ReactNode;
  className?: string;
}) {
  if (!Icon) {
    return <p className={cn("px-5 py-8 text-center text-[13px] text-dim", className)}>{text}</p>;
  }
  return (
    <div className={cn("flex flex-col items-center px-5 py-12 text-center", className)}>
      <span className="grid size-14 place-items-center rounded-full bg-sub text-dim">
        <Icon size={24} strokeWidth={1.5} aria-hidden="true" />
      </span>
      {title && <h3 className="mt-4 text-base font-medium">{title}</h3>}
      <p className="mt-1 max-w-xs text-[13px] text-dim">{text}</p>
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

// 403/404: the content is not there for this user.
export function isUnavailable(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 403 || error.status === 404);
}

export function UnavailableState({ className }: { className?: string }) {
  return <EmptyState text="Bu içerik görüntülenemiyor." className={className} />;
}

// Inline text for a failed query. The failure itself is already toasted globally.
export function QueryError({ error, className }: { error: unknown; className?: string }) {
  return isUnavailable(error) ? (
    <UnavailableState className={className} />
  ) : (
    <EmptyState text="Veri alınamadı." className={className} />
  );
}
