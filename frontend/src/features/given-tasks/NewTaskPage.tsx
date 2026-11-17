import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useNavigate } from "react-router";
import { ApiError } from "@/api/problem";
import { useCreateTask } from "@/api/queries/task";
import { useClassroomStudents, useMyClassrooms } from "@/api/queries/user";
import { Card } from "@/components/ui/Card";
import { Field, INPUT, PRIMARY_BUTTON, TEXTAREA } from "@/components/ui/form";
import { FormSelect } from "@/components/ui/FormSelect";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import {
  RECURRENCE_LABELS,
  WEEKDAYS,
  taskFormSchema,
  toCreateRequest,
  type TaskFormValues,
} from "@/lib/taskForm";
import { dateKey } from "@/lib/week";

const BACK = { label: "Görevler", to: "/given-tasks" };

export function NewTaskPage() {
  const navigate = useNavigate();
  const classrooms = useMyClassrooms();
  const create = useCreateTask();
  // One key per form: a double click or retry returns the first response instead of a second task.
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const today = dateKey(new Date());

  const {
    register,
    control,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<TaskFormValues>({
    resolver: zodResolver(taskFormSchema),
    defaultValues: {
      title: "",
      description: "",
      targetType: "CLASSROOM",
      classroomId: "",
      studentId: "",
      recurrence: "ONCE",
      recurrenceDays: [],
      startDate: today,
      endDate: "",
      dueTime: "23:59",
    },
  });
  const targetType = useWatch({ control, name: "targetType" });
  const recurrence = useWatch({ control, name: "recurrence" });
  const classroomId = useWatch({ control, name: "classroomId" });
  const students = useClassroomStudents(targetType === "STUDENT" ? classroomId : undefined);

  const onSubmit = handleSubmit((values) =>
    create.mutate(
      { body: toCreateRequest(values), idempotencyKey },
      {
        onSuccess: (task) => navigate(`/given-tasks/${task.id}`),
        onError: (error) => {
          // The server rejected this body for good; the next attempt is a new request.
          if (error instanceof ApiError && error.status < 500) {
            setIdempotencyKey(crypto.randomUUID());
          }
        },
      },
    ),
  );

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current="Yeni görev" />}
        title="Yeni görev"
      />
      <Card className="mt-6 max-w-2xl p-5">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <Field label="Başlık" htmlFor="task-title" error={errors.title?.message}>
            <input id="task-title" maxLength={200} className={INPUT} {...register("title")} />
          </Field>
          <Field label="Açıklama" htmlFor="task-description" error={errors.description?.message}>
            <textarea
              id="task-description"
              rows={4}
              maxLength={5000}
              className={TEXTAREA}
              {...register("description")}
            />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Kime" htmlFor="task-target">
              <FormSelect
                id="task-target"
                control={control}
                name="targetType"
                options={[
                  { value: "CLASSROOM", label: "Sınıf" },
                  { value: "STUDENT", label: "Öğrenci" },
                ]}
              />
            </Field>
            <Field label="Sınıf" htmlFor="task-classroom" error={errors.classroomId?.message}>
              <FormSelect
                id="task-classroom"
                control={control}
                name="classroomId"
                onValueChange={() => setValue("studentId", "")}
                options={(classrooms.data ?? []).map((c) => ({
                  value: c.id ?? "",
                  label: c.name ?? "",
                }))}
              />
            </Field>
          </div>
          {targetType === "STUDENT" && (
            <Field label="Öğrenci" htmlFor="task-student" error={errors.studentId?.message}>
              <FormSelect
                id="task-student"
                control={control}
                name="studentId"
                disabled={!classroomId}
                loading={!!classroomId && students.isPending}
                placeholder={classroomId ? "Seçin" : "Önce sınıf seçin"}
                options={(students.data ?? []).map((s) => ({
                  value: s.studentId ?? "",
                  label: `${s.firstName} ${s.lastName}`,
                }))}
              />
            </Field>
          )}

          <Field label="Tekrar" htmlFor="task-recurrence">
            <FormSelect
              id="task-recurrence"
              control={control}
              name="recurrence"
              options={Object.entries(RECURRENCE_LABELS).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Field>
          {recurrence === "WEEKLY" && (
            <Field label="Günler" error={errors.recurrenceDays?.message}>
              <div role="group" aria-label="Günler" className="flex flex-wrap gap-2">
                {WEEKDAYS.map((day) => (
                  <label
                    key={day.value}
                    className="inline-flex h-8 cursor-pointer items-center gap-2 rounded-full border border-line px-3 text-[13px] has-checked:border-ink has-checked:bg-ink has-checked:text-white"
                  >
                    <input
                      type="checkbox"
                      value={day.value}
                      className="sr-only"
                      {...register("recurrenceDays")}
                    />
                    {day.short}
                  </label>
                ))}
              </div>
            </Field>
          )}

          <div className="grid gap-4 sm:grid-cols-3">
            <Field
              label={recurrence === "ONCE" ? "Veriliş günü" : "Başlangıç"}
              htmlFor="task-start"
              error={errors.startDate?.message}
            >
              <input
                id="task-start"
                type="date"
                min={today}
                className={INPUT}
                {...register("startDate")}
              />
            </Field>
            <Field
              label={recurrence === "ONCE" ? "Son teslim günü" : "Bitiş (isteğe bağlı)"}
              htmlFor="task-end"
              error={errors.endDate?.message}
            >
              <input
                id="task-end"
                type="date"
                min={today}
                className={INPUT}
                {...register("endDate")}
              />
            </Field>
            <Field label="Son teslim saati" htmlFor="task-time" error={errors.dueTime?.message}>
              <input id="task-time" type="time" className={INPUT} {...register("dueTime")} />
            </Field>
          </div>

          <div>
            <button type="submit" disabled={create.isPending} className={PRIMARY_BUTTON}>
              {create.isPending ? "Oluşturuluyor…" : "Görevi oluştur"}
            </button>
          </div>
        </form>
      </Card>
    </>
  );
}
