import { ArrowRightLeft, BookmarkCheck, Check, Clock, X, type LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

export type StatusTone = "success" | "danger" | "info" | "warning" | "neutral";

// Colors live in index.css (--status-<tone>-*). Class strings are written out so Tailwind can see them.
// solid: white glyph in a filled circle; otherwise a plain line icon in the tone color.
const TONES: Record<
  StatusTone,
  { tile: string; circle: string; line: string; Icon: LucideIcon; solid: boolean }
> = {
  success: {
    tile: "bg-(--status-success-bg) text-(--status-success-fg)",
    circle: "bg-(--status-success-strong)",
    line: "text-(--status-success-strong)",
    Icon: Check,
    solid: true,
  },
  danger: {
    tile: "bg-(--status-danger-bg) text-(--status-danger-fg)",
    circle: "bg-(--status-danger-strong)",
    line: "text-(--status-danger-strong)",
    Icon: X,
    solid: true,
  },
  info: {
    tile: "bg-(--status-info-bg) text-(--status-info-fg)",
    circle: "bg-(--status-info-strong)",
    line: "text-(--status-info-strong)",
    Icon: ArrowRightLeft,
    solid: false,
  },
  warning: {
    tile: "bg-(--status-warning-bg) text-(--status-warning-fg)",
    circle: "bg-(--status-warning-strong)",
    line: "text-(--status-warning-strong)",
    Icon: Clock,
    solid: true,
  },
  neutral: {
    tile: "bg-(--status-neutral-bg) text-(--status-neutral-fg)",
    circle: "bg-(--status-neutral-strong)",
    line: "text-(--status-neutral-strong)",
    Icon: BookmarkCheck,
    solid: false,
  },
};

const SIZES = {
  sm: { badge: "h-6 gap-1.5 px-2 text-xs", tile: "size-6", circle: "size-4", glyph: 10, line: 14 },
  md: { badge: "h-8 gap-2 px-3 text-sm", tile: "size-8", circle: "size-5", glyph: 12, line: 18 },
} as const;

export function StatusBadge({
  tone,
  label,
  iconOnly = false,
  size = "md",
}: {
  tone: StatusTone;
  label: string;
  iconOnly?: boolean;
  size?: "sm" | "md";
}) {
  const t = TONES[tone];
  const s = SIZES[size];
  const icon = t.solid ? (
    <span
      aria-hidden="true"
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full text-white",
        t.circle,
        s.circle,
      )}
    >
      <t.Icon size={s.glyph} strokeWidth={3} />
    </span>
  ) : (
    <t.Icon
      aria-hidden="true"
      size={s.line}
      strokeWidth={1.75}
      className={cn("shrink-0", t.line)}
    />
  );

  if (iconOnly) {
    return (
      <span
        role="img"
        aria-label={label}
        title={label}
        className={cn(
          "inline-flex shrink-0 items-center justify-center rounded-[8px]",
          t.tile,
          s.tile,
        )}
      >
        {icon}
      </span>
    );
  }
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-[8px] font-normal whitespace-nowrap",
        t.tile,
        s.badge,
      )}
    >
      {icon}
      {label}
    </span>
  );
}
