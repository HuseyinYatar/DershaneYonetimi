import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useCreateSchedule, useUpdateSchedule } from "@/api/queries/attendance";
import { useBranchClassrooms, useBranchTeachers, useUserSubjects } from "@/api/queries/user";
import { useBranchId } from "@/auth/useBranchId";
import { Card, CardHeader } from "@/components/ui/Card";
import { Field, PRIMARY_BUTTON } from "@/components/ui/form";
import { FormDatePicker, FormTimePicker } from "@/components/ui/FormPickers";
import { FormSelect } from "@/components/ui/FormSelect";
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
    control,
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
            <FormSelect
              id="schedule-classroom"
              control={control}
              name="classroomId"
              disabled={editing}
              options={(classrooms.data ?? [])
                .filter((c) => c.active || c.id === initial.classroomId)
                .map((c) => ({ value: c.id ?? "", label: c.name ?? "" }))}
            />
          </Field>
          <Field label="Öğretmen" htmlFor="schedule-teacher" error={errors.teacherId?.message}>
            <FormSelect
              id="schedule-teacher"
              control={control}
              name="teacherId"
              options={(teachers.data ?? [])
                .filter((t) => t.active || t.id === initial.teacherId)
                .map((t) => ({ value: t.id ?? "", label: fullName(t) }))}
            />
          </Field>
          <Field label="Ders" htmlFor="schedule-subject" error={errors.subjectId?.message}>
            <FormSelect
              id="schedule-subject"
              control={control}
              name="subjectId"
              options={(subjects.data ?? [])
                .filter((s) => s.active || s.id === initial.subjectId)
                .map((s) => ({ value: s.id ?? "", label: s.name ?? "" }))}
            />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Gün" htmlFor="schedule-day">
            <FormSelect
              id="schedule-day"
              control={control}
              name="dayOfWeek"
              options={WEEKDAYS.map((d) => ({ value: d.value, label: DAY_LABELS[d.value] }))}
            />
          </Field>
          <Field label="Başlangıç saati" htmlFor="schedule-start" error={errors.startsAt?.message}>
            <FormTimePicker id="schedule-start" control={control} name="startsAt" />
          </Field>
          <Field label="Bitiş saati" htmlFor="schedule-end" error={errors.endsAt?.message}>
            <FormTimePicker id="schedule-end" control={control} name="endsAt" />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field
            label="Geçerli (başlangıç)"
            htmlFor="schedule-from"
            error={errors.validFrom?.message}
          >
            <FormDatePicker id="schedule-from" control={control} name="validFrom" />
          </Field>
          <Field
            label="Geçerli (bitiş, isteğe bağlı)"
            htmlFor="schedule-to"
            error={errors.validTo?.message}
          >
            <FormDatePicker
              id="schedule-to"
              control={control}
              name="validTo"
              clearable
              placeholder="Süresiz"
            />
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
