import { z } from "zod";
import { clock, toMinutes, type TimetableSetting } from "./scheduleBoard";

// The same limits the attendance service enforces (TIMETABLE_SETTING_INVALID).
const FIRST_START = { min: "05:00", max: "20:00" };
const LAST_END_MINUTES = 23 * 60 + 59;

const whole = (label: string, min: number, max: number) =>
  z
    .string()
    .regex(/^\d+$/, `${label} tam sayı olmalı.`)
    .refine((v) => Number(v) >= min && Number(v) <= max, `${label} ${min}–${max} arasında olmalı.`);

// Form fields are strings (inputs); `toSetting` converts after validation.
export const timetableFormSchema = z
  .object({
    firstLessonStart: z
      .string()
      .regex(/^\d{2}:\d{2}$/, "İlk ders saati gerekli.")
      .refine(
        (v) => v >= FIRST_START.min && v <= FIRST_START.max,
        `İlk ders ${FIRST_START.min} ile ${FIRST_START.max} arasında başlamalı.`,
      ),
    lessonMinutes: whole("Ders süresi", 20, 120),
    breakMinutes: whole("Teneffüs", 0, 60),
    lessonCount: whole("Ders sayısı", 1, 16),
  })
  .superRefine((v, ctx) => {
    // Only when the numbers themselves are fine: one message per mistake.
    const end = withinLimits(v) ? lastEndMinutes(v) : undefined;
    if (end !== undefined && end > LAST_END_MINUTES) {
      ctx.addIssue({
        code: "custom",
        path: ["lessonCount"],
        message: "Son ders gün içinde (23:59'a kadar) bitmeli.",
      });
    }
  });

const withinLimits = (v: TimetableFormValues) =>
  [v.lessonMinutes, v.breakMinutes, v.lessonCount].every((f) => /^\d+$/.test(f)) &&
  Number(v.lessonMinutes) >= 20 &&
  Number(v.lessonMinutes) <= 120 &&
  Number(v.breakMinutes) <= 60 &&
  Number(v.lessonCount) >= 1 &&
  Number(v.lessonCount) <= 16;

export type TimetableFormValues = z.infer<typeof timetableFormSchema>;

export function toFormValues(s: TimetableSetting): TimetableFormValues {
  return {
    firstLessonStart: s.firstLessonStart.slice(0, 5),
    lessonMinutes: String(s.lessonMinutes),
    breakMinutes: String(s.breakMinutes),
    lessonCount: String(s.lessonCount),
  };
}

// The lunch lesson is placed on the board, not in this form: the caller passes the current one along.
export function toSetting(v: TimetableFormValues, lunchLesson: number): TimetableSetting {
  return {
    firstLessonStart: v.firstLessonStart,
    lessonMinutes: Number(v.lessonMinutes),
    breakMinutes: Number(v.breakMinutes),
    lessonCount: Number(v.lessonCount),
    // A lunch lesson beyond the new last lesson no longer exists.
    lunchLesson: lunchLesson <= Number(v.lessonCount) ? lunchLesson : 0,
  };
}

// End of the last lesson in minutes, or undefined while the numbers are not (yet) numbers.
function lastEndMinutes(v: TimetableFormValues): number | undefined {
  if (!/^\d{2}:\d{2}$/.test(v.firstLessonStart)) return undefined;
  const fields = [v.lessonMinutes, v.breakMinutes, v.lessonCount];
  if (!fields.every((f) => /^\d+$/.test(f))) return undefined;
  const [lesson, pause, count] = fields.map(Number);
  return toMinutes(v.firstLessonStart) + count * lesson + (count - 1) * pause;
}

// Live preview line under the form, e.g. "12 ders · 08:10 – 20:00"; nothing while the form is incomplete.
export function previewOf(v: TimetableFormValues): string | undefined {
  const end = lastEndMinutes(v);
  if (end === undefined || end > LAST_END_MINUTES || Number(v.lessonCount) < 1) return undefined;
  return `${v.lessonCount} ders · ${v.firstLessonStart} – ${clock(end)}`;
}
