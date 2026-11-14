import type { components } from "@/api/schema/task";
import type { StatusTone } from "@/components/StatusBadge";

export type AssignmentStatus = NonNullable<
  components["schemas"]["AssignmentSummaryResponse"]["status"]
>;

// Record keys keep the mapping exhaustive: a new enum value fails the type check.
const ASSIGNMENT_BADGES: Record<AssignmentStatus, { tone: StatusTone; label: string }> = {
  ASSIGNED: { tone: "warning", label: "Bekliyor" },
  SUBMITTED: { tone: "info", label: "Teslim edildi" },
  OVERDUE: { tone: "danger", label: "Gecikti" },
  GRADED: { tone: "success", label: "Değerlendirildi" },
  CANCELLED: { tone: "neutral", label: "İptal edildi" },
};

export function assignmentBadge(status: AssignmentStatus) {
  return ASSIGNMENT_BADGES[status];
}
