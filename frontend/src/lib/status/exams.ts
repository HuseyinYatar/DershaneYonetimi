import type { components } from "@/api/schema/exam-analysis";
import type { StatusTone } from "@/components/StatusBadge";

type ExamStatus = components["schemas"]["ExamStatus"];

// Record keys keep the mapping exhaustive: a new enum value fails the type check.
const EXAM_BADGES: Record<ExamStatus, { tone: StatusTone; label: string }> = {
  DRAFT: { tone: "neutral", label: "Taslak" },
  KEY_READY: { tone: "info", label: "Cevap anahtarı hazır" },
  PROCESSED: { tone: "warning", label: "Açıklanmadı" },
  PUBLISHED: { tone: "success", label: "Açıklandı" },
};

export function examBadge(status: ExamStatus) {
  return EXAM_BADGES[status];
}
