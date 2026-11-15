import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useCreateSchedule, useUpdateSchedule } from "@/api/queries/attendance";
import { useBranchClassrooms, useBranchTeachers, useUserSubjects } from "@/api/queries/user";
import { useBranchId } from "@/auth/useBranchId";
import { Card, CardHeader } from "@/components/ui/Card";
import { Field, INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { PillButton } from "@/components/ui/Pill";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  DAY_LABELS,
  scheduleFormSchema,
  toCreateSchedule,
  toUpdateSchedule,
  type ScheduleFormValues,
} from "@/lib/scheduleForm";
import { WEEKDAYS } from "@/lib/taskForm";
import { useIdempotencyKey } from "@/lib/useIdempotencyKey";
import { fullName } from "@/lib/userForm";

export function ScheduleFormSkeleton() {
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

const EMPTY: ScheduleFormValues = {
  classroomId: "",
  teacherId: "",
  subjectId: "",
  dayOfWeek: "MONDAY",
  startsAt: "",
  endsAt: "",
  validFrom: "",
  validTo: "",
};

// Create (no scheduleId) or edit. Editing replaces the changeable fields; the classroom stays fixed.
export function ScheduleForm({
  scheduleId,
  initial = EMPTY,
  onDone,
}: {
  scheduleId?: string;
  initial?: ScheduleFormValues;
  onDone: () => void;
}) {
  const branchId = useBranchId();
  const classrooms = useBranchClassrooms();
  const teachers = useBranchTeachers();
  const subjects = useUserSubjects();
  const create = useCreateSchedule();
  const update = useUpdateSchedule(scheduleId ?? "");
  const idempotency = useIdempotencyKey();
  const editing = !!scheduleId;
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ScheduleFormValues>({
    resolver: zodResolver(scheduleFormSchema),
    defaultValues: initial,
  });

  if (classrooms.isPending || teachers.isPending || subjects.isPending) {
    return <ScheduleFormSkeleton />;
  }

  const onSubmit = handleSubmit((values) => {
    if (editing) {
      update.mutate(toUpdateSchedule(values), { onSuccess: onDone });
      return;
    }
    if (!branchId) return;
    create.mutate(
      { body: toCreateSchedule(values, branchId), idempotencyKey: idempotency.key },
      {
        onSuccess: () => {
          idempotency.renew();
          onDone();
        },
        onError: idempotency.renewOnClientError,
      },
    );
  });
  const pending = create.isPending || update.isPending;

  return (
    <Card className="p-5">
      <CardHeader title={editing ? "Programı düzenle" : "Yeni haftalık program"} />
      <p className="mt-2 text-[13px] text-dim">
        Dersler her gün çalışan bir görevle ileri 7 gün için otomatik oluşturulur. Değişiklik
        yalnızca sonradan oluşacak derslere uygulanır.
      </p>
      <form onSubmit={onSubmit} noValidate className="mt-4 flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Sınıf" htmlFor="schedule-classroom" error={errors.classroomId?.message}>
            <select
              id="schedule-classroom"
              disabled={editing}
              className={INPUT}
              {...register("classroomId")}
            >
              <option value="">Seçin</option>
              {(classrooms.data ?? [])
                .filter((c) => c.active || c.id === initial.classroomId)
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Öğretmen" htmlFor="schedule-teacher" error={errors.teacherId?.message}>
            <select id="schedule-teacher" className={INPUT} {...register("teacherId")}>
              <option value="">Seçin</option>
              {(teachers.data ?? [])
                .filter((t) => t.active || t.id === initial.teacherId)
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {fullName(t)}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Ders" htmlFor="schedule-subject" error={errors.subjectId?.message}>
            <select id="schedule-subject" className={INPUT} {...register("subjectId")}>
              <option value="">Seçin</option>
              {(subjects.data ?? [])
                .filter((s) => s.active || s.id === initial.subjectId)
                .map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
            </select>
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Gün" htmlFor="schedule-day">
            <select id="schedule-day" className={INPUT} {...register("dayOfWeek")}>
              {WEEKDAYS.map((d) => (
                <option key={d.value} value={d.value}>
                  {DAY_LABELS[d.value]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Başlangıç saati" htmlFor="schedule-start" error={errors.startsAt?.message}>
            <input id="schedule-start" type="time" className={INPUT} {...register("startsAt")} />
          </Field>
          <Field label="Bitiş saati" htmlFor="schedule-end" error={errors.endsAt?.message}>
            <input id="schedule-end" type="time" className={INPUT} {...register("endsAt")} />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field
            label="Geçerli (başlangıç)"
            htmlFor="schedule-from"
            error={errors.validFrom?.message}
          >
            <input id="schedule-from" type="date" className={INPUT} {...register("validFrom")} />
          </Field>
          <Field
            label="Geçerli (bitiş, isteğe bağlı)"
            htmlFor="schedule-to"
            error={errors.validTo?.message}
          >
            <input id="schedule-to" type="date" className={INPUT} {...register("validTo")} />
          </Field>
        </div>
        <div className="flex gap-2">
          <button
            type="submit"
            disabled={pending || (!editing && !branchId)}
            className={PRIMARY_BUTTON}
          >
            {pending ? "Kaydediliyor…" : editing ? "Kaydet" : "Programı oluştur"}
          </button>
          <PillButton onClick={onDone} className="h-10 px-4">
            Vazgeç
          </PillButton>
        </div>
      </form>
    </Card>
  );
}
