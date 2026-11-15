import { School } from "lucide-react";
import { useState } from "react";
import {
  useBranchClassrooms,
  useCreateTeacherAssignment,
  useDeleteTeacherAssignment,
  useTeacherAssignments,
  useUserSubjects,
} from "@/api/queries/user";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { PillButton } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { useIdempotencyKey } from "@/lib/useIdempotencyKey";

export function TeacherAssignmentsCardSkeleton() {
  return (
    <Card className="p-5">
      <CardHeader title="Sınıf atamaları" />
      <div className="mt-4 -mx-5">
        <SkeletonRows rows={3} />
      </div>
    </Card>
  );
}

// Which subject the teacher teaches in which classroom.
export function TeacherAssignmentsCard({
  teacherId,
  active,
}: {
  teacherId: string;
  active: boolean;
}) {
  const assignments = useTeacherAssignments(teacherId);
  const classrooms = useBranchClassrooms();
  const subjects = useUserSubjects();
  const create = useCreateTeacherAssignment(teacherId);
  const remove = useDeleteTeacherAssignment(teacherId);
  const idempotency = useIdempotencyKey();
  const [classroomId, setClassroomId] = useState("");
  const [subjectId, setSubjectId] = useState("");

  if (assignments.isPending || classrooms.isPending || subjects.isPending) {
    return <TeacherAssignmentsCardSkeleton />;
  }
  if (assignments.isError) {
    return (
      <Card className="p-5">
        <CardHeader title="Sınıf atamaları" />
        <QueryError error={assignments.error} />
      </Card>
    );
  }
  const list = assignments.data ?? [];

  return (
    <Card className="p-5">
      <CardHeader title="Sınıf atamaları" />
      {list.length === 0 ? (
        <EmptyState
          icon={School}
          title="Atama yok"
          text="Bu öğretmen henüz bir sınıfa atanmamış."
        />
      ) : (
        <ul className="mt-2">
          {list.map((a) => (
            <li
              key={a.id}
              className="anim-fade flex items-center justify-between gap-3 border-t border-line py-3 first:border-t-0"
            >
              <div className="text-sm">
                <span className="font-medium">{a.classroomName}</span>
                <span className="text-dim"> · {a.subjectName}</span>
              </div>
              <PillButton
                disabled={remove.isPending}
                onClick={() => {
                  if (window.confirm("Atama kaldırılsın mı?")) remove.mutate(a.id ?? "");
                }}
              >
                Kaldır
              </PillButton>
            </li>
          ))}
        </ul>
      )}
      {active && (
        <div className="mt-3 flex flex-col gap-2 border-t border-line pt-4 sm:flex-row">
          <select
            aria-label="Sınıf"
            value={classroomId}
            onChange={(e) => setClassroomId(e.target.value)}
            className={INPUT}
          >
            <option value="">Sınıf seç</option>
            {(classrooms.data ?? [])
              .filter((c) => c.active)
              .map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
          </select>
          <select
            aria-label="Ders"
            value={subjectId}
            onChange={(e) => setSubjectId(e.target.value)}
            className={INPUT}
          >
            <option value="">Ders seç</option>
            {(subjects.data ?? [])
              .filter((s) => s.active)
              .map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
          </select>
          <button
            type="button"
            disabled={!classroomId || !subjectId || create.isPending}
            onClick={() =>
              create.mutate(
                { body: { classroomId, subjectId }, idempotencyKey: idempotency.key },
                {
                  onSuccess: () => {
                    idempotency.renew();
                    setClassroomId("");
                    setSubjectId("");
                  },
                  onError: idempotency.renewOnClientError,
                },
              )
            }
            className={`${PRIMARY_BUTTON} shrink-0`}
          >
            Ata
          </button>
        </div>
      )}
    </Card>
  );
}
