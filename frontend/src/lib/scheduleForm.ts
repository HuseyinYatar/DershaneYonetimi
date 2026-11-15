import { z } from "zod";
import type { components } from "@/api/schema/attendance";
import { WEEKDAYS, type Weekday } from "./taskForm";

type Schemas = components["schemas"];
export type CreateScheduleRequest = Schemas["CreateScheduleRequest"];
export type UpdateScheduleRequest = Schemas["UpdateScheduleRequest"];
export type CreateSessionRequest = Schemas["CreateSessionRequest"];

export const DAY_LABELS: Record<Weekday, string> = {
  MONDAY: "Pazartesi",
  TUESDAY: "Salı",
  WEDNESDAY: "Çarşamba",
  THURSDAY: "Perşembe",
  FRIDAY: "Cuma",
  SATURDAY: "Cumartesi",
  SUNDAY: "Pazar",
};

const REQUIRED = (message: string) => z.string().min(1, message);
const DAYS = WEEKDAYS.map((d) => d.value) as [Weekday, ...Weekday[]];

// Times are "HH:mm" strings from <input type="time">, so they compare as text.
const checkTimes = (v: { startsAt: string; endsAt: string }, ctx: z.RefinementCtx) => {
  if (v.startsAt && v.endsAt && v.endsAt <= v.startsAt) {
    ctx.addIssue({
      code: "custom",
      path: ["endsAt"],
      message: "Bitiş saati başlangıçtan sonra olmalı.",
    });
  }
};

// The same rules the attendance service enforces (ClassSchedule: end after start, validTo not before validFrom).
export const scheduleFormSchema = z
  .object({
    classroomId: REQUIRED("Sınıf seçin."),
    teacherId: REQUIRED("Öğretmen seçin."),
    subjectId: REQUIRED("Ders seçin."),
    dayOfWeek: z.enum(DAYS),
    startsAt: REQUIRED("Başlangıç saati gerekli."),
    endsAt: REQUIRED("Bitiş saati gerekli."),
    validFrom: REQUIRED("Başlangıç tarihi gerekli."),
    validTo: z.string(),
  })
  .superRefine((v, ctx) => {
    checkTimes(v, ctx);
    if (v.validTo && v.validFrom && v.validTo < v.validFrom) {
      ctx.addIssue({
        code: "custom",
        path: ["validTo"],
        message: "Bitiş tarihi başlangıçtan önce olamaz.",
      });
    }
  });

export type ScheduleFormValues = z.infer<typeof scheduleFormSchema>;

export function toCreateSchedule(v: ScheduleFormValues, branchId: string): CreateScheduleRequest {
  return { branchId, ...toUpdateSchedule(v), classroomId: v.classroomId };
}

// The classroom of a schedule cannot change, so the update leaves it out.
export function toUpdateSchedule(v: ScheduleFormValues): UpdateScheduleRequest {
  return {
    teacherId: v.teacherId,
    subjectId: v.subjectId,
    dayOfWeek: v.dayOfWeek,
    startsAt: v.startsAt,
    endsAt: v.endsAt,
    validFrom: v.validFrom,
    validTo: v.validTo || undefined,
  };
}

export const sessionFormSchema = z
  .object({
    classroomId: REQUIRED("Sınıf seçin."),
    teacherId: REQUIRED("Öğretmen seçin."),
    subjectId: REQUIRED("Ders seçin."),
    sessionDate: REQUIRED("Tarih gerekli."),
    startsAt: REQUIRED("Başlangıç saati gerekli."),
    endsAt: REQUIRED("Bitiş saati gerekli."),
  })
  .superRefine(checkTimes);

export type SessionFormValues = z.infer<typeof sessionFormSchema>;

export function toCreateSession(v: SessionFormValues, branchId: string): CreateSessionRequest {
  return { branchId, ...v };
}
