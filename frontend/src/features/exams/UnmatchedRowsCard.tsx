import { CircleCheck } from "lucide-react";
import { useState } from "react";
import { useIgnoreRow, useMatchRow, useUnmatchedRows } from "@/api/queries/exam";
import { useBranchStudents } from "@/api/queries/user";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { INPUT } from "@/components/ui/form";
import { PillButton } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { isMatchable, unmatchedReasonLabel } from "@/lib/status/admin";
import { fullName } from "@/lib/userForm";

export function UnmatchedRowsCardSkeleton() {
  return (
    <Card className="p-5">
      <CardHeader title="Eşleşmeyen satırlar" />
      <div className="-mx-5 mt-4">
        <SkeletonRows rows={3} />
      </div>
    </Card>
  );
}

// Rows no student was found for. The admin links one to a student of the exam's branch (the service
// refuses other branches and a second row for the same student) or ignores it; nothing is deleted.
export function UnmatchedRowsCard({ jobId }: { jobId: string }) {
  const rows = useUnmatchedRows(jobId, "PENDING");
  const students = useBranchStudents();
  const match = useMatchRow();
  const ignore = useIgnoreRow();
  const [choice, setChoice] = useState<Record<string, string>>({});

  if (rows.isPending || students.isPending) return <UnmatchedRowsCardSkeleton />;
  if (rows.isError) {
    return (
      <Card className="p-5">
        <CardHeader title="Eşleşmeyen satırlar" />
        <QueryError error={rows.error} />
      </Card>
    );
  }
  const list = rows.data ?? [];
  const options = (students.data ?? []).filter((s) => s.active);

  return (
    <Card className="p-5">
      <CardHeader
        title={
          <>
            Eşleşmeyen satırlar <span className="font-normal text-dim">{list.length}</span>
          </>
        }
      />
      {list.length === 0 ? (
        <EmptyState
          icon={CircleCheck}
          title="Bekleyen satır yok"
          text="Tüm satırlar eşleşti veya yok sayıldı."
        />
      ) : (
        <ul className="mt-2">
          {list.map((row) => (
            <li
              key={row.id}
              className="anim-fade flex flex-col gap-2 border-t border-line py-3 first:border-t-0 lg:flex-row lg:items-center lg:justify-between"
            >
              <div className="min-w-0 text-sm">
                <span className="font-medium">
                  Satır {row.lineNo} · no {row.rawStudentNo || "—"}
                </span>
                <div className="text-[13px] text-dim">{unmatchedReasonLabel(row.reason)}</div>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {isMatchable(row.reason) && (
                  <>
                    <select
                      aria-label={`Satır ${row.lineNo} için öğrenci`}
                      value={choice[row.id] ?? ""}
                      onChange={(e) => setChoice({ ...choice, [row.id]: e.target.value })}
                      className={`${INPUT} lg:w-64`}
                    >
                      <option value="">Öğrenci seç</option>
                      {options.map((s) => (
                        <option key={s.id} value={s.id}>
                          {fullName(s)} · {s.studentNumber}
                        </option>
                      ))}
                    </select>
                    <PillButton
                      disabled={!choice[row.id] || match.isPending}
                      onClick={() => match.mutate({ rowId: row.id, studentId: choice[row.id] })}
                    >
                      Bağla
                    </PillButton>
                  </>
                )}
                <PillButton
                  disabled={ignore.isPending}
                  onClick={() => {
                    if (window.confirm("Satır yok sayılsın mı?")) ignore.mutate(row.id);
                  }}
                >
                  Yok say
                </PillButton>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
