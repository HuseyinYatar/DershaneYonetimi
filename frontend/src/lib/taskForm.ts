import { z } from "zod";
import type { components } from "@/api/schema/task";
import { formatClock, formatDayMonth, parseDateOnly } from "./format";
import { dateKey } from "./week";

type Task = components["schemas"]["TaskResponse"];
type CreateRequest = components["schemas"]["CreateTaskRequest"];
type EvaluateRequest = components["schemas"]["EvaluateAssignmentRequest"];
type Recurrence = CreateRequest["recurrence"];
export type Weekday = NonNullable<CreateRequest["recurrenceDays"]>[number];

export const WEEKDAYS: { value: Weekday; short: string }[] = [
  { value: "MONDAY", short: "Pzt" },
  { value: "TUESDAY", short: "Sal" },
  { value: "WEDNESDAY", short: "Çar" },
  { value: "THURSDAY", short: "Per" },
  { value: "FRIDAY", short: "Cum" },
  { value: "SATURDAY", short: "Cmt" },
  { value: "SUNDAY", short: "Paz" },
];

export const RECURRENCE_LABELS: Record<Recurrence, string> = {
  ONCE: "Tek seferlik",
  DAILY: "Günlük",
  WEEKLY: "Haftalık",
};

// "Son teslim 12 Kas, 23:59" / "Her gün, 18:00 · 12 Kas itibaren" / "Pzt, Çar, 18:00 · 12 Kas – 20 Ara"
export function describeSchedule(
  task: Pick<Task, "recurrence" | "recurrenceDays" | "startDate" | "endDate" | "dueTime">,
): string {
  const day = (value?: string) => (value ? formatDayMonth(parseDateOnly(value)) : "");
  const time = formatClock(task.dueTime);
  if (task.recurrence === "ONCE") return `Son teslim ${day(task.endDate)}, ${time}`;
  const when =
    task.recurrence === "DAILY"
      ? "Her gün"
      : WEEKDAYS.filter((d) => task.recurrenceDays?.includes(d.value))
          .map((d) => d.short)
          .join(", ");
  const range = task.endDate
    ? `${day(task.startDate)} – ${day(task.endDate)}`
    : `${day(task.startDate)} itibaren`;
  return `${when}, ${time} · ${range}`;
}

const TITLE = z
  .string()
  .trim()
  .min(1, "Başlık gerekli.")
  .max(200, "Başlık en çok 200 karakter olabilir.");
const DESCRIPTION = z.string().max(5000, "Açıklama en çok 5000 karakter olabilir.");

// The same rules the task service enforces (Task.create), so most mistakes never reach the server.
export const taskFormSchema = z
  .object({
    title: TITLE,
    description: DESCRIPTION,
    targetType: z.enum(["CLASSROOM", "STUDENT"]),
    classroomId: z.string().min(1, "Sınıf seçin."),
    studentId: z.string(),
    recurrence: z.enum(["ONCE", "DAILY", "WEEKLY"]),
    recurrenceDays: z.array(z.enum(WEEKDAYS.map((d) => d.value) as [Weekday, ...Weekday[]])),
    startDate: z.string().min(1, "Başlangıç tarihi gerekli."),
    endDate: z.string(),
    dueTime: z.string().min(1, "Son teslim saati gerekli."),
  })
  .superRefine((v, ctx) => {
    const fail = (path: string, message: string) =>
      ctx.addIssue({ code: "custom", path: [path], message });
    if (v.targetType === "STUDENT" && !v.studentId) fail("studentId", "Öğrenci seçin.");
    if (v.recurrence === "WEEKLY" && v.recurrenceDays.length === 0) {
      fail("recurrenceDays", "En az bir gün seçin.");
    }
    if (v.startDate && v.startDate < dateKey(new Date())) {
      fail("startDate", "Başlangıç tarihi geçmişte olamaz.");
    }
    if (v.recurrence === "ONCE" && !v.endDate) fail("endDate", "Son teslim günü gerekli.");
    if (v.endDate && v.startDate && v.endDate < v.startDate) {
      fail("endDate", "Bitiş tarihi başlangıçtan önce olamaz.");
    }
    if (
      v.recurrence === "ONCE" &&
      v.endDate &&
      v.dueTime &&
      new Date(`${v.endDate}T${v.dueTime}`) <= new Date()
    ) {
      fail("dueTime", "Son teslim zamanı geçmişte olamaz.");
    }
  });

export type TaskFormValues = z.infer<typeof taskFormSchema>;

export function toCreateRequest(v: TaskFormValues): CreateRequest {
  return {
    title: v.title.trim(),
    description: v.description.trim(),
    targetType: v.targetType,
    targetId: v.targetType === "STUDENT" ? v.studentId : v.classroomId,
    recurrence: v.recurrence,
    // The service rejects weekdays on anything but weekly tasks.
    recurrenceDays: v.recurrence === "WEEKLY" ? v.recurrenceDays : undefined,
    startDate: v.startDate,
    endDate: v.endDate || undefined,
    dueTime: v.dueTime,
  };
}

export const taskEditSchema = z.object({ title: TITLE, description: DESCRIPTION });

export type TaskEditValues = z.infer<typeof taskEditSchema>;

// The score is typed into a text input, so it stays a string until the request is built.
export const evaluationSchema = z
  .object({
    score: z.string(),
    feedback: z.string().max(5000, "Geri bildirim en çok 5000 karakter olabilir."),
  })
  .superRefine((v, ctx) => {
    const score = v.score.trim();
    const fail = (message: string) => ctx.addIssue({ code: "custom", path: ["score"], message });
    if (score === "" && v.feedback.trim() === "") {
      fail("Puan veya geri bildirimden en az biri gerekli.");
    } else if (score !== "" && !(/^\d{1,3}$/.test(score) && Number(score) <= 100)) {
      fail("Puan 0 ile 100 arasında bir tam sayı olmalı.");
    }
  });

export type EvaluationValues = z.infer<typeof evaluationSchema>;

export function toEvaluationRequest(v: EvaluationValues): EvaluateRequest {
  return {
    score: v.score.trim() === "" ? undefined : Number(v.score),
    feedback: v.feedback.trim() || undefined,
  };
}
