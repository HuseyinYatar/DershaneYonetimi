import { useState } from "react";
import type { components } from "@/api/schema/attendance";
import { useTakeAttendance } from "@/api/queries/attendance";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { PillButton, SegButton } from "@/components/ui/Pill";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  ATTENDANCE_CHOICES,
  allMarked,
  markAll,
  markedCount,
  toEntries,
  type Marks,
} from "@/lib/roster";
import { attendanceBadge } from "@/lib/status/attendance";

type Row = components["schemas"]["RosterRowResponse"];

export function TakeAttendanceSkeleton() {
  return (
    <Card className="p-5">
      <CardHeader title="Öğrenciler" />
      <div role="status" aria-label="Yükleniyor" className="mt-4 flex flex-col gap-3">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-12" />
        ))}
      </div>
    </Card>
  );
}

// One row per roster student, no preselected status: every student has to be marked on purpose.
// `requireReason`: an admin taking attendance after the session day ended must say why.
export function TakeAttendance({
  sessionId,
  students,
  requireReason = false,
}: {
  sessionId: string;
  students: Row[];
  requireReason?: boolean;
}) {
  const [marks, setMarks] = useState<Marks>({});
  const [reason, setReason] = useState("");
  const take = useTakeAttendance(sessionId);
  const ids = students.map((s) => s.studentId ?? "");

  if (students.length === 0) {
    return (
      <Card>
        <EmptyState text="Bu derste kayıtlı öğrenci yok." />
      </Card>
    );
  }

  return (
    <Card className="p-5">
      <CardHeader
        title="Öğrenciler"
        action={
          <PillButton onClick={() => setMarks(markAll(ids, "PRESENT"))}>
            Hepsini var işaretle
          </PillButton>
        }
      />
      <ul className="mt-2">
        {students.map((student) => {
          const id = student.studentId ?? "";
          const name = student.fullName ?? "Öğrenci";
          return (
            <li
              key={id}
              className="flex flex-col gap-2 border-t border-line py-3 first:border-t-0 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2 text-sm font-medium">
                  {name}
                  {student.excused && <StatusBadge tone="info" label="Mazeretli" size="sm" />}
                </div>
                <div className="text-[13px] text-dim">{student.studentNumber}</div>
              </div>
              <div role="group" aria-label={`${name} yoklaması`} className="flex flex-wrap gap-1.5">
                {ATTENDANCE_CHOICES.map((status) => (
                  <SegButton
                    key={status}
                    active={marks[id] === status}
                    onClick={() => setMarks({ ...marks, [id]: status })}
                  >
                    {attendanceBadge(status).label}
                  </SegButton>
                ))}
              </div>
            </li>
          );
        })}
      </ul>
      {requireReason && (
        <input
          aria-label="Geç yoklama gerekçesi"
          placeholder="Gerekçe (dersin günü geçti, zorunlu)"
          maxLength={500}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          className={`${INPUT} mt-4`}
        />
      )}
      <div className="mt-4 flex items-center justify-between gap-3 border-t border-line pt-4">
        <span className="text-[13px] text-dim">
          {markedCount(ids, marks)} / {ids.length} öğrenci işaretlendi
        </span>
        <button
          type="button"
          disabled={!allMarked(ids, marks) || take.isPending || (requireReason && !reason.trim())}
          onClick={() =>
            take.mutate({
              entries: toEntries(ids, marks),
              reason: requireReason ? reason.trim() : undefined,
            })
          }
          className={PRIMARY_BUTTON}
        >
          {take.isPending ? "Kaydediliyor…" : "Yoklamayı kaydet"}
        </button>
      </div>
    </Card>
  );
}
