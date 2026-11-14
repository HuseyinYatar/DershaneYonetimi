import type { components } from "@/api/schema/attendance";
import type { StatusTone } from "@/components/StatusBadge";

type Badge = { tone: StatusTone; label: string };
type AttendanceStatus = components["schemas"]["CorrectRecordRequest"]["status"];
type ExcuseStatus = NonNullable<components["schemas"]["ExcuseResponse"]["status"]>;

// Record keys keep both mappings exhaustive: a new enum value fails the type check.
const ATTENDANCE_BADGES: Record<AttendanceStatus, Badge> = {
  PRESENT: { tone: "success", label: "Var" },
  ABSENT: { tone: "danger", label: "Yok" },
  LATE: { tone: "warning", label: "Geç" },
  EXCUSED: { tone: "info", label: "İzinli" },
};

const EXCUSE_BADGES: Record<ExcuseStatus, Badge> = {
  PENDING: { tone: "warning", label: "Bekliyor" },
  APPROVED: { tone: "success", label: "Onaylandı" },
  REJECTED: { tone: "danger", label: "Reddedildi" },
};

export function attendanceBadge(status: AttendanceStatus) {
  return ATTENDANCE_BADGES[status];
}

export function excuseBadge(status: ExcuseStatus) {
  return EXCUSE_BADGES[status];
}
