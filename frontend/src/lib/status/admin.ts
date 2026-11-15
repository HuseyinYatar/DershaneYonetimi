import type { components } from "@/api/schema/exam-analysis";
import type { StatusTone } from "@/components/StatusBadge";

type Badge = { tone: StatusTone; label: string };
type JobStatus = components["schemas"]["JobStatus"];
type UnmatchedReason = components["schemas"]["UnmatchedReason"];
type MatchStatus = components["schemas"]["MatchStatus"];

// Record keys keep the mappings exhaustive: a new enum value fails the type check.
const JOB_BADGES: Record<JobStatus, Badge> = {
  QUEUED: { tone: "neutral", label: "Sırada" },
  PROCESSING: { tone: "warning", label: "İşleniyor" },
  COMPLETED: { tone: "success", label: "Tamamlandı" },
  FAILED: { tone: "danger", label: "Başarısız" },
};

const REASON_LABELS: Record<UnmatchedReason, string> = {
  STUDENT_NOT_FOUND: "Öğrenci bulunamadı",
  DUPLICATE_STUDENT: "Aynı öğrenci birden çok satırda",
  INVALID_BOOKLET: "Geçersiz kitapçık",
  MALFORMED_LINE: "Bozuk satır",
};

const MATCH_BADGES: Record<MatchStatus, Badge> = {
  PENDING: { tone: "warning", label: "Bekliyor" },
  MATCHED: { tone: "success", label: "Eşleşti" },
  IGNORED: { tone: "neutral", label: "Yok sayıldı" },
};

export function jobBadge(status: JobStatus) {
  return JOB_BADGES[status];
}

export function jobIsRunning(status: JobStatus | undefined): boolean {
  return status === "QUEUED" || status === "PROCESSING";
}

export function unmatchedReasonLabel(reason: UnmatchedReason) {
  return REASON_LABELS[reason];
}

// The service only scores these two reasons; the other rows can only be ignored.
export function isMatchable(reason: UnmatchedReason): boolean {
  return reason === "STUDENT_NOT_FOUND" || reason === "DUPLICATE_STUDENT";
}

export function matchBadge(status: MatchStatus) {
  return MATCH_BADGES[status];
}

export function activeBadge(active: boolean | undefined): Badge {
  return active === false
    ? { tone: "neutral", label: "Pasif" }
    : { tone: "success", label: "Aktif" };
}
