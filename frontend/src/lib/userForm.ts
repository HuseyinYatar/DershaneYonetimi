import { z } from "zod";
import type { components } from "@/api/schema/user";

type Schemas = components["schemas"];
export type CreateStudentRequest = Schemas["CreateStudentRequest"];
export type CreateTeacherRequest = Schemas["CreateTeacherRequest"];
export type CreateGuardianRequest = Schemas["CreateGuardianRequest"];
export type UpdateStudentRequest = Schemas["UpdateStudentRequest"];
export type UpdateTeacherRequest = Schemas["UpdateTeacherRequest"];
export type UpdateGuardianRequest = Schemas["UpdateGuardianRequest"];
export type Relationship = Schemas["LinkGuardianRequest"]["relationship"];

export const USER_KINDS = ["student", "teacher", "guardian"] as const;
export type UserKind = (typeof USER_KINDS)[number];

export const KIND_LABELS: Record<UserKind, { singular: string; plural: string }> = {
  student: { singular: "Öğrenci", plural: "Öğrenciler" },
  teacher: { singular: "Öğretmen", plural: "Öğretmenler" },
  guardian: { singular: "Veli", plural: "Veliler" },
};

// URL segment of a kind: /users/students/:id
export const KIND_PATHS: Record<UserKind, string> = {
  student: "students",
  teacher: "teachers",
  guardian: "guardians",
};

export function userPath(kind: UserKind, id: string): string {
  return `/users/${KIND_PATHS[kind]}/${id}`;
}

export const RELATIONSHIP_LABELS: Record<Relationship, string> = {
  MOTHER: "Anne",
  FATHER: "Baba",
  LEGAL_GUARDIAN: "Yasal vasi",
  OTHER: "Diğer",
};

// Same formats as the user service's request records (UserPatterns).
const PHONE = /^(?=(?:\D*\d){10})\+?[0-9 ()-]{10,20}$/;
const STUDENT_NUMBER = /^[0-9A-Za-z-]{1,20}$/;

const NAME = (label: string) =>
  z.string().trim().min(1, `${label} gerekli.`).max(80, `${label} en çok 80 karakter olabilir.`);

// One schema for the three kinds; the kind decides which fields are required (superRefine below).
export const userFormSchema = z
  .object({
    kind: z.enum(USER_KINDS),
    firstName: NAME("Ad"),
    lastName: NAME("Soyad"),
    email: z.string().trim().max(254, "E-posta en çok 254 karakter olabilir."),
    phone: z.string().trim(),
    studentNumber: z.string().trim(),
    gradeLevel: z.string(),
    targetExam: z.enum(["YKS", "LGS"]),
    specialtySubjectId: z.string(),
  })
  .superRefine((v, ctx) => {
    const fail = (path: string, message: string) =>
      ctx.addIssue({ code: "custom", path: [path], message });
    if (v.email && !z.email().safeParse(v.email).success) {
      fail("email", "Geçerli bir e-posta girin.");
    }
    if (v.phone && !PHONE.test(v.phone)) fail("phone", "Geçerli bir telefon numarası girin.");
    if (v.kind === "teacher") {
      if (!v.email) fail("email", "Öğretmen için e-posta gerekli.");
      if (!v.specialtySubjectId) fail("specialtySubjectId", "Branş seçin.");
    }
    if (v.kind === "guardian" && !v.phone) fail("phone", "Veli için telefon gerekli.");
    if (v.kind === "student") {
      if (!STUDENT_NUMBER.test(v.studentNumber)) {
        fail("studentNumber", "Öğrenci numarası harf, rakam veya tire olmalı (en çok 20).");
      }
      const grade = Number(v.gradeLevel);
      if (!Number.isInteger(grade) || grade < 1 || grade > 13) {
        fail("gradeLevel", "Sınıf seviyesi 1 ile 13 arasında olmalı.");
      }
    }
  });

export type UserFormValues = z.infer<typeof userFormSchema>;

export const EMPTY_USER_FORM: Omit<UserFormValues, "kind"> = {
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
  studentNumber: "",
  gradeLevel: "",
  targetExam: "YKS",
  specialtySubjectId: "",
};

// Empty optional fields are left out: the service reads null as "unchanged" / "none".
const optional = (value: string) => value.trim() || undefined;

// The kind tag lets the API hooks pick the endpoint without casting the body.
export type CreateCall =
  | { kind: "student"; body: CreateStudentRequest }
  | { kind: "teacher"; body: CreateTeacherRequest }
  | { kind: "guardian"; body: CreateGuardianRequest };

export type UpdateCall =
  | { kind: "student"; body: UpdateStudentRequest }
  | { kind: "teacher"; body: UpdateTeacherRequest }
  | { kind: "guardian"; body: UpdateGuardianRequest };

export function toCreateRequest(v: UserFormValues, branchId: string): CreateCall {
  const base = {
    branchId,
    firstName: v.firstName.trim(),
    lastName: v.lastName.trim(),
    email: optional(v.email),
    phone: optional(v.phone),
  };
  if (v.kind === "student") {
    const body = {
      ...base,
      studentNumber: v.studentNumber.trim(),
      gradeLevel: Number(v.gradeLevel),
      targetExam: v.targetExam,
    };
    return { kind: "student", body };
  }
  if (v.kind === "teacher") {
    const body = { ...base, email: v.email.trim(), specialtySubjectId: v.specialtySubjectId };
    return { kind: "teacher", body };
  }
  return { kind: "guardian", body: { ...base, phone: v.phone.trim() } };
}

export function toUpdateRequest(v: UserFormValues): UpdateCall {
  const base = {
    firstName: v.firstName.trim(),
    lastName: v.lastName.trim(),
    email: optional(v.email),
    phone: optional(v.phone),
  };
  if (v.kind === "student") {
    const body = {
      ...base,
      studentNumber: v.studentNumber.trim(),
      gradeLevel: Number(v.gradeLevel),
      targetExam: v.targetExam,
    };
    return { kind: "student", body };
  }
  if (v.kind === "teacher") {
    return { kind: "teacher", body: { ...base, specialtySubjectId: v.specialtySubjectId } };
  }
  return { kind: "guardian", body: base };
}

// A person's full name for tables and headings.
export function fullName(person: { firstName?: string; lastName?: string }): string {
  return `${person.firstName ?? ""} ${person.lastName ?? ""}`.trim();
}
