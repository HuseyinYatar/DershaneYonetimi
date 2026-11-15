import { cn } from "@/lib/utils";

export function Skeleton({ className }: { className?: string }) {
  // A block-level span, so it is also valid inside <p> and <h1> (page header placeholders).
  return (
    <span
      aria-hidden="true"
      className={cn("block animate-pulse rounded-xl bg-line/70", className)}
    />
  );
}

// Stacked rows shaped like a table body.
export function SkeletonRows({ rows = 4 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Yükleniyor" className="flex flex-col gap-3 px-5 pb-5">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-12" />
      ))}
    </div>
  );
}
