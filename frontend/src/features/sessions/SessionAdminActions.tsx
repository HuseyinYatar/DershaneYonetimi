import { useState } from "react";
import { useCancelSession, useReassignTeacher } from "@/api/queries/attendance";
import { useBranchTeachers } from "@/api/queries/user";
import { Card, CardHeader } from "@/components/ui/Card";
import { PRIMARY_BUTTON } from "@/components/ui/form";
import { PillButton } from "@/components/ui/Pill";
import { useConfirm } from "@/lib/useConfirm";
import { Select } from "@/components/ui/Select";
import { Skeleton } from "@/components/ui/Skeleton";
import { fullName } from "@/lib/userForm";

export function SessionAdminActionsSkeleton() {
  return (
    <Card className="p-5">
      <CardHeader title="Yönetici işlemleri" />
      <div role="status" aria-label="Yükleniyor" className="mt-4 flex flex-col gap-3">
        <Skeleton className="h-10" />
        <Skeleton className="h-10 w-32" />
      </div>
    </Card>
  );
}

// Only a PLANNED session can be cancelled or handed to a substitute teacher (the service enforces it).
export function SessionAdminActions({
  sessionId,
  teacherId,
}: {
  sessionId: string;
  teacherId: string;
}) {
  const teachers = useBranchTeachers();
  const ask = useConfirm();
  const cancel = useCancelSession(sessionId);
  const reassign = useReassignTeacher(sessionId);
  const [substitute, setSubstitute] = useState("");

  if (teachers.isPending) return <SessionAdminActionsSkeleton />;

  return (
    <Card className="p-5">
      <CardHeader title="Yönetici işlemleri" />
      <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <Select
          aria-label="Yerine giren öğretmen"
          placeholder="Yerine giren öğretmen"
          value={substitute}
          onChange={setSubstitute}
          options={(teachers.data ?? [])
            .filter((t) => t.active && t.id !== teacherId)
            .map((t) => ({ value: t.id ?? "", label: fullName(t) }))}
          className="w-full sm:w-72"
        />
        <button
          type="button"
          disabled={!substitute || reassign.isPending}
          onClick={() => reassign.mutate(substitute, { onSuccess: () => setSubstitute("") })}
          className={PRIMARY_BUTTON}
        >
          {reassign.isPending ? "Kaydediliyor…" : "Öğretmeni değiştir"}
        </button>
      </div>
      <div className="mt-4 border-t border-line pt-4">
        <PillButton
          disabled={cancel.isPending}
          onClick={async () => {
            if (
              await ask({
                title: "Bu ders iptal edilsin mi?",
                text: "Yoklama alınmayacak.",
                confirmLabel: "Dersi iptal et",
                danger: true,
              })
            )
              cancel.mutate();
          }}
        >
          Dersi iptal et
        </PillButton>
      </div>
    </Card>
  );
}
