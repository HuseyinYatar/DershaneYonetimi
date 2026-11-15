import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useCreateSession } from "@/api/queries/attendance";
import { useBranchClassrooms, useBranchTeachers, useUserSubjects } from "@/api/queries/user";
import { useBranchId } from "@/auth/useBranchId";
import { Card, CardHeader } from "@/components/ui/Card";
import { Field, INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { PillButton } from "@/components/ui/Pill";
import { Skeleton } from "@/components/ui/Skeleton";
import { fullName } from "@/lib/userForm";
import { sessionFormSchema, toCreateSession, type SessionFormValues } from "@/lib/scheduleForm";
import { useIdempotencyKey } from "@/lib/useIdempotencyKey";

export function NewSessionFormSkeleton() {
  return (
    <Card className="p-5">
      <div role="status" aria-label="Yükleniyor" className="flex flex-col gap-4">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
      </div>
    </Card>
  );
}

// An extra (makeup / study) session outside any weekly schedule; only admins open these.
export function NewSessionForm({ day, onDone }: { day: string; onDone: () => void }) {
  const branchId = useBranchId();
  const classrooms = useBranchClassrooms();
  const teachers = useBranchTeachers();
  const subjects = useUserSubjects();
  const create = useCreateSession();
  const idempotency = useIdempotencyKey();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SessionFormValues>({
    resolver: zodResolver(sessionFormSchema),
    defaultValues: {
      classroomId: "",
      teacherId: "",
      subjectId: "",
      sessionDate: day,
      startsAt: "",
      endsAt: "",
    },
  });

  if (classrooms.isPending || teachers.isPending || subjects.isPending) {
    return <NewSessionFormSkeleton />;
  }

  const onSubmit = handleSubmit((values) => {
    if (!branchId) return;
    create.mutate(
      { body: toCreateSession(values, branchId), idempotencyKey: idempotency.key },
      {
        onSuccess: () => {
          idempotency.renew();
          onDone();
        },
        onError: idempotency.renewOnClientError,
      },
    );
  });

  return (
    <Card className="p-5">
      <CardHeader title="Ek ders aç" />
      <form onSubmit={onSubmit} noValidate className="mt-4 flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Sınıf" htmlFor="session-classroom" error={errors.classroomId?.message}>
            <select id="session-classroom" className={INPUT} {...register("classroomId")}>
              <option value="">Seçin</option>
              {(classrooms.data ?? [])
                .filter((c) => c.active)
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Öğretmen" htmlFor="session-teacher" error={errors.teacherId?.message}>
            <select id="session-teacher" className={INPUT} {...register("teacherId")}>
              <option value="">Seçin</option>
              {(teachers.data ?? [])
                .filter((t) => t.active)
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {fullName(t)}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Ders" htmlFor="session-subject" error={errors.subjectId?.message}>
            <select id="session-subject" className={INPUT} {...register("subjectId")}>
              <option value="">Seçin</option>
              {(subjects.data ?? [])
                .filter((s) => s.active)
                .map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
            </select>
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Tarih" htmlFor="session-date" error={errors.sessionDate?.message}>
            <input id="session-date" type="date" className={INPUT} {...register("sessionDate")} />
          </Field>
          <Field label="Başlangıç" htmlFor="session-start" error={errors.startsAt?.message}>
            <input id="session-start" type="time" className={INPUT} {...register("startsAt")} />
          </Field>
          <Field label="Bitiş" htmlFor="session-end" error={errors.endsAt?.message}>
            <input id="session-end" type="time" className={INPUT} {...register("endsAt")} />
          </Field>
        </div>
        <div className="flex gap-2">
          <button type="submit" disabled={create.isPending || !branchId} className={PRIMARY_BUTTON}>
            {create.isPending ? "Oluşturuluyor…" : "Dersi oluştur"}
          </button>
          <PillButton onClick={onDone} className="h-10 px-4">
            Vazgeç
          </PillButton>
        </div>
      </form>
    </Card>
  );
}
