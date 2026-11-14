import type { components } from "@/api/schema/attendance";

type Summary = components["schemas"]["AttendanceSummaryResponse"];

// Attended = present or late; excused absences do not count as attendance. null when nothing was recorded.
export function attendanceRate(summary: Summary | undefined): number | null {
  const total = summary?.total ?? 0;
  if (!summary || total <= 0) return null;
  return Math.round((((summary.present ?? 0) + (summary.late ?? 0)) / total) * 100);
}

// Gauge segments in legend order; colors are the status tones, so arc and badges match.
export function attendanceSegments(summary: Summary) {
  return [
    { value: summary.present ?? 0, color: "var(--status-success-strong)" },
    { value: summary.late ?? 0, color: "var(--status-warning-strong)" },
    { value: summary.absent ?? 0, color: "var(--status-danger-strong)" },
    { value: summary.excused ?? 0, color: "var(--status-info-strong)" },
  ];
}
