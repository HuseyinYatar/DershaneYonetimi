import { ArrowDown, ArrowUp } from "lucide-react";
import { formatDelta } from "@/lib/format";
import { cn } from "@/lib/utils";

// Green up / red down chip with the absolute net difference.
export function DeltaBadge({ delta }: { delta: number }) {
  const up = delta >= 0;
  const Icon = up ? ArrowUp : ArrowDown;
  return (
    <span
      className={cn(
        "inline-flex h-6 items-center gap-1 rounded-[7px] border px-[9px] text-xs font-medium whitespace-nowrap",
        up
          ? "border-[#b8e6c9] bg-[#ddf5e6] text-[#14653a]"
          : "border-[#f0c4c4] bg-[#f8e1e1] text-[#a12a2a]",
      )}
    >
      <Icon size={12} aria-hidden="true" />
      {formatDelta(Math.abs(delta))}
    </span>
  );
}
