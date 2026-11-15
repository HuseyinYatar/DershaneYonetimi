import { z } from "zod";
import type { components } from "@/api/schema/user";

type CreateRequest = components["schemas"]["CreateClassroomRequest"];

// Same rules as the user service: name up to 60 chars, grade 1-13, term "2026-2027".
export const classroomFormSchema = z.object({
  name: z.string().trim().min(1, "Sınıf adı gerekli.").max(60, "Ad en çok 60 karakter olabilir."),
  gradeLevel: z.string().refine((v) => {
    const grade = Number(v);
    return Number.isInteger(grade) && grade >= 1 && grade <= 13;
  }, "Seviye 1 ile 13 arasında olmalı."),
  term: z
    .string()
    .trim()
    .regex(/^\d{4}-\d{4}$/, "Dönem 2026-2027 biçiminde olmalı."),
});

export type ClassroomFormValues = z.infer<typeof classroomFormSchema>;

export function toCreateClassroom(v: ClassroomFormValues, branchId: string): CreateRequest {
  return {
    branchId,
    name: v.name.trim(),
    gradeLevel: Number(v.gradeLevel),
    term: v.term.trim(),
  };
}
