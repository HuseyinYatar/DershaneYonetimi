import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useNavigate } from "react-router";
import { ApiError } from "@/api/problem";
import { useCreateTask } from "@/api/queries/task";
import { useClassroomStudents, useMyClassrooms } from "@/api/queries/user";
import { Card } from "@/components/ui/Card";
import { Field, INPUT, PRIMARY_BUTTON, TEXTAREA } from "@/components/ui/form";
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
              <select id="task-target" className={INPUT} {...register("targetType")}>
                <option value="CLASSROOM">Sınıf</option>
                <option value="STUDENT">Öğrenci</option>
              </select>
            </Field>
            <Field label="Sınıf" htmlFor="task-classroom" error={errors.classroomId?.message}>
              <select
                id="task-classroom"
                className={INPUT}
                {...register("classroomId", { onChange: () => setValue("studentId", "") })}
              >
                <option value="">Seçin</option>
                {(classrooms.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          {targetType === "STUDENT" && (
            <Field label="Öğrenci" htmlFor="task-student" error={errors.studentId?.message}>
              <select
                id="task-student"
                disabled={!classroomId || students.isPending}
                className={INPUT}
                {...register("studentId")}
              >
                <option value="">{classroomId ? "Seçin" : "Önce sınıf seçin"}</option>
                {(students.data ?? []).map((s) => (
                  <option key={s.studentId} value={s.studentId}>
                    {s.firstName} {s.lastName}
                  </option>
                ))}
              </select>
            </Field>
          )}

          <Field label="Tekrar" htmlFor="task-recurrence">
            <select id="task-recurrence" className={INPUT} {...register("recurrence")}>
              {Object.entries(RECURRENCE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
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
