import type { components } from "@/api/schema/attendance";
import { StatusBadge } from "@/components/StatusBadge";
import { formatInt } from "@/lib/format";
import { attendanceBadge } from "@/lib/status/attendance";

type Summary = components["schemas"]["AttendanceSummaryResponse"];

// Counts per status as StatusBadge legend, under the gauge.
export function AttendanceLegend({ summary }: { summary: Summary }) {
  const rows = [
    ["PRESENT", summary.present],
    ["LATE", summary.late],
    ["ABSENT", summary.absent],
    ["EXCUSED", summary.excused],
  ] as const;
  return (
    <ul className="mt-15 grid grid-cols-2 gap-y-4 text-[13px]">
      {rows.map(([status, count]) => {
        const badge = attendanceBadge(status);
        return (
          // Fixed-width badge cell keeps the counts aligned right next to the badges.
          <li key={status} className="flex items-center justify-center gap-2">
            <span className="w-[5.25rem] shrink-0">
              <StatusBadge tone={badge.tone} label={badge.label} />
            </span>
            <b className="text-[15px] font-medium">{formatInt(count ?? 0)}</b>
          </li>
        );
      })}
    </ul>
  );
}
