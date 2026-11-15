import { useState, type FormEvent } from "react";
import type { components } from "@/api/schema/attendance";
import { useCorrectRecord } from "@/api/queries/attendance";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { PillButton, SegButton } from "@/components/ui/Pill";
import { ATTENDANCE_CHOICES } from "@/lib/roster";
import { attendanceBadge, type AttendanceStatus } from "@/lib/status/attendance";

type Row = components["schemas"]["RosterRowResponse"];

// The service enforces the teacher's correction window; its error shows as a toast.
function CorrectionForm({
  sessionId,
  studentId,
  onDone,
}: {
  sessionId: string;
  studentId: string;
  onDone: () => void;
}) {
  const correct = useCorrectRecord(sessionId);
  const [status, setStatus] = useState<AttendanceStatus>();
  const [reason, setReason] = useState("");

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!status) return;
    correct.mutate({ studentId, status, reason: reason.trim() }, { onSuccess: onDone });
  };

  return (
    <form onSubmit={onSubmit} className="mt-3 flex flex-col gap-3 rounded-2xl bg-sub p-3">
      <div role="group" aria-label="Yeni durum" className="flex flex-wrap gap-1.5">
        {ATTENDANCE_CHOICES.map((choice) => (
          <SegButton key={choice} active={status === choice} onClick={() => setStatus(choice)}>
            {attendanceBadge(choice).label}
          </SegButton>
        ))}
      </div>
      <input
        aria-label="Düzeltme gerekçesi"
        placeholder="Gerekçe"
        maxLength={500}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        className={INPUT}
      />
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={!status || !reason.trim() || correct.isPending}
          className={PRIMARY_BUTTON}
        >
          {correct.isPending ? "Kaydediliyor…" : "Düzelt"}
        </button>
        <PillButton onClick={onDone} className="h-10 px-4">
          Vazgeç
        </PillButton>
      </div>
    </form>
  );
}

export function CompletedAttendance({
  sessionId,
  students,
}: {
  sessionId: string;
  students: Row[];
}) {
  const [editing, setEditing] = useState<string>();

  return (
    <Card className="p-5">
      <CardHeader title="Yoklama" />
      {students.length === 0 ? (
        <EmptyState text="Bu derste kayıtlı öğrenci yok." />
      ) : (
        <ul className="mt-2">
          {students.map((student) => {
            const id = student.studentId ?? "";
            const badge = student.status ? attendanceBadge(student.status) : undefined;
            return (
              <li key={id} className="border-t border-line py-3 first:border-t-0">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-sm font-medium">{student.fullName ?? "Öğrenci"}</div>
                    <div className="text-[13px] text-dim">{student.studentNumber}</div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {badge ? <StatusBadge tone={badge.tone} label={badge.label} /> : "—"}
                    <PillButton onClick={() => setEditing(editing === id ? undefined : id)}>
                      Düzelt
                    </PillButton>
                  </div>
                </div>
                {editing === id && (
                  <CorrectionForm
                    sessionId={sessionId}
                    studentId={id}
                    onDone={() => setEditing(undefined)}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
