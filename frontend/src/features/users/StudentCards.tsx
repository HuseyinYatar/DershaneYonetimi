import { useState } from "react";
import {
  useAssignClassroom,
  useBranchClassrooms,
  useBranchGuardians,
  useLinkGuardian,
  useRemoveFromClassroom,
  useStudentGuardian,
  useUnlinkGuardian,
} from "@/api/queries/user";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PRIMARY_BUTTON } from "@/components/ui/form";
import { PillButton } from "@/components/ui/Pill";
import { useConfirm } from "@/lib/useConfirm";
import { Select } from "@/components/ui/Select";
import { Skeleton } from "@/components/ui/Skeleton";
import { RELATIONSHIP_LABELS, fullName, type Relationship } from "@/lib/userForm";

function CardSkeleton({ title }: { title: string }) {
  return (
    <Card className="p-5">
      <CardHeader title={title} />
      <div role="status" aria-label="Yükleniyor" className="mt-4 flex flex-col gap-3">
        <Skeleton className="h-10" />
        <Skeleton className="h-10 w-40" />
      </div>
    </Card>
  );
}

export function GuardianCardSkeleton() {
  return <CardSkeleton title="Veli" />;
}

// A student has at most one guardian; linking another replaces the old link on the server.
export function GuardianCard({ studentId, active }: { studentId: string; active: boolean }) {
  const current = useStudentGuardian(studentId);
  const guardians = useBranchGuardians();
  const link = useLinkGuardian(studentId);
  const ask = useConfirm();
  const unlink = useUnlinkGuardian(studentId);
  const [guardianId, setGuardianId] = useState("");
  const [relationship, setRelationship] = useState<Relationship>("MOTHER");

  if (current.isPending || guardians.isPending) return <GuardianCardSkeleton />;
  if (current.isError) {
    return (
      <Card className="p-5">
        <CardHeader title="Veli" />
        <QueryError error={current.error} />
      </Card>
    );
  }
  const guardian = current.data;

  return (
    <Card className="p-5">
      <CardHeader
        title="Veli"
        action={
          guardian && (
            <PillButton
              disabled={unlink.isPending}
              onClick={async () => {
                if (
                  await ask({
                    title: "Veli bağı kaldırılsın mı?",
                    confirmLabel: "Bağı kaldır",
                    danger: true,
                  })
                )
                  unlink.mutate();
              }}
            >
              Bağı kaldır
            </PillButton>
          )
        }
      />
      {guardian ? (
        <div className="mt-3 text-sm">
          <div className="font-medium">{fullName(guardian)}</div>
          <div className="text-[13px] text-dim">
            {RELATIONSHIP_LABELS[guardian.relationship ?? "OTHER"]} · {guardian.phone ?? "—"}
          </div>
        </div>
      ) : (
        <EmptyState text="Bu öğrenciye bağlı veli yok." className="px-0 py-4 text-left" />
      )}
      {active && (
        <div className="mt-3 flex flex-col gap-2 border-t border-line pt-4 sm:flex-row">
          <Select
            aria-label="Veli"
            className="w-full"
            placeholder={guardian ? "Başka veli seç" : "Veli seç"}
            value={guardianId}
            onChange={setGuardianId}
            options={(guardians.data ?? [])
              .filter((g) => g.active && g.id !== guardian?.guardianId)
              .map((g) => ({ value: g.id ?? "", label: `${fullName(g)} · ${g.phone}` }))}
          />
          <Select
            aria-label="Yakınlık"
            className="w-full sm:w-44"
            value={relationship}
            onChange={(value) => setRelationship(value as Relationship)}
            options={Object.entries(RELATIONSHIP_LABELS).map(([value, label]) => ({
              value,
              label,
            }))}
          />
          <button
            type="button"
            disabled={!guardianId || link.isPending}
            onClick={() =>
              link.mutate({ guardianId, relationship }, { onSuccess: () => setGuardianId("") })
            }
            className={`${PRIMARY_BUTTON} shrink-0`}
          >
            Bağla
          </button>
        </div>
      )}
    </Card>
  );
}

export function EnrollmentCardSkeleton() {
  return <CardSkeleton title="Sınıf" />;
}

// The service rejects a classroom of another level or branch (GRADE_LEVEL_MISMATCH); the list is
// narrowed to the student's level so the usual mistake cannot be made.
export function EnrollmentCard({
  studentId,
  gradeLevel,
  active,
}: {
  studentId: string;
  gradeLevel: number;
  active: boolean;
}) {
  const classrooms = useBranchClassrooms();
  const assign = useAssignClassroom(studentId);
  const ask = useConfirm();
  const remove = useRemoveFromClassroom();
  const [classroomId, setClassroomId] = useState("");

  if (classrooms.isPending) return <EnrollmentCardSkeleton />;
  if (classrooms.isError) {
    return (
      <Card className="p-5">
        <CardHeader title="Sınıf" />
        <QueryError error={classrooms.error} />
      </Card>
    );
  }
  const options = (classrooms.data ?? []).filter((c) => c.active && c.gradeLevel === gradeLevel);

  return (
    <Card className="p-5">
      <CardHeader
        title="Sınıf"
        action={
          active && (
            <PillButton
              disabled={remove.isPending}
              onClick={async () => {
                if (
                  await ask({
                    title: "Öğrenci sınıftan çıkarılsın mı?",
                    confirmLabel: "Sınıftan çıkar",
                    danger: true,
                  })
                )
                  remove.mutate(studentId);
              }}
            >
              Sınıftan çıkar
            </PillButton>
          )
        }
      />
      <p className="mt-2 text-[13px] text-dim">
        Yeni sınıfa eklenen öğrencinin eski üyeliği bugünün tarihiyle kapanır. Sınıf listesi
        yalnızca {gradeLevel}. sınıf seviyesindekileri gösterir.
      </p>
      {active && (
        <div className="mt-3 flex flex-col gap-2 sm:flex-row">
          <Select
            aria-label="Sınıf"
            className="w-full"
            placeholder={options.length === 0 ? "Uygun sınıf yok" : "Sınıf seç"}
            value={classroomId}
            onChange={setClassroomId}
            options={options.map((c) => ({ value: c.id ?? "", label: `${c.name} · ${c.term}` }))}
          />
          <button
            type="button"
            disabled={!classroomId || assign.isPending}
            onClick={() => assign.mutate(classroomId, { onSuccess: () => setClassroomId("") })}
            className={`${PRIMARY_BUTTON} shrink-0`}
          >
            Sınıfa ekle
          </button>
        </div>
      )}
    </Card>
  );
}
