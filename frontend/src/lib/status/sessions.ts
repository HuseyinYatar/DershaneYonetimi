import type { components } from "@/api/schema/attendance";
import type { StatusTone } from "@/components/StatusBadge";

export type SessionStatus = NonNullable<components["schemas"]["SessionResponse"]["status"]>;

// Record keys keep the mapping exhaustive: a new enum value fails the type check.
const SESSION_BADGES: Record<SessionStatus, { tone: StatusTone; label: string }> = {
  PLANNED: { tone: "warning", label: "Yoklama bekliyor" },
  COMPLETED: { tone: "success", label: "Yoklama alındı" },
  CANCELLED: { tone: "neutral", label: "İptal edildi" },
  EXPIRED: { tone: "danger", label: "Yoklama alınmadı" },
};

export function sessionBadge(status: SessionStatus) {
  return SESSION_BADGES[status];
}
