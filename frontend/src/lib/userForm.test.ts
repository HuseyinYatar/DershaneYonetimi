import { describe, expect, it } from "vitest";
import { classroomFormSchema, toCreateClassroom } from "./classroomForm";
import {
  EMPTY_USER_FORM,
  toCreateRequest,
  toUpdateRequest,
  userFormSchema,
  type UserFormValues,
} from "./userForm";

const form = (patch: Partial<UserFormValues>): UserFormValues => ({
  ...EMPTY_USER_FORM,
  kind: "student",
  firstName: "Ayşe",
  lastName: "Yılmaz",
  studentNumber: "1042",
  gradeLevel: "12",
  ...patch,
});

const messages = (values: UserFormValues) => {
  const result = userFormSchema.safeParse(values);
  return result.success ? [] : result.error.issues.map((i) => `${String(i.path[0])}: ${i.message}`);
};

describe("userFormSchema", () => {
  it("accepts a student without e-mail or phone", () => {
    expect(messages(form({}))).toEqual([]);
  });

  it("rejects a student number with illegal characters and an out-of-range grade", () => {
    expect(messages(form({ studentNumber: "10 42", gradeLevel: "14" }))).toHaveLength(2);
    expect(messages(form({ gradeLevel: "" }))).toHaveLength(1);
  });

  it("requires e-mail and specialty for teachers", () => {
    const issues = messages(form({ kind: "teacher" }));
    expect(issues).toContain("email: Öğretmen için e-posta gerekli.");
    expect(issues).toContain("specialtySubjectId: Branş seçin.");
    expect(
      messages(form({ kind: "teacher", email: "t@dershane.test", specialtySubjectId: "s-1" })),
    ).toEqual([]);
  });

  it("requires a phone for guardians and checks its format", () => {
    expect(messages(form({ kind: "guardian" }))).toContain("phone: Veli için telefon gerekli.");
    expect(messages(form({ kind: "guardian", phone: "12345" }))).toContain(
      "phone: Geçerli bir telefon numarası girin.",
    );
    expect(messages(form({ kind: "guardian", phone: "+90 532 111 22 33" }))).toEqual([]);
  });

  it("rejects a malformed e-mail", () => {
    expect(messages(form({ email: "not-an-email" }))).toContain(
      "email: Geçerli bir e-posta girin.",
    );
  });
});

describe("request builders", () => {
  it("leaves empty optional fields out and trims", () => {
    expect(toCreateRequest(form({ firstName: " Ayşe " }), "b-1")).toEqual({
      kind: "student",
      body: {
        branchId: "b-1",
        firstName: "Ayşe",
        lastName: "Yılmaz",
        email: undefined,
        phone: undefined,
        studentNumber: "1042",
        gradeLevel: 12,
        targetExam: "YKS",
      },
    });
  });

  it("builds a teacher update with the specialty and no student fields", () => {
    const update = toUpdateRequest(
      form({ kind: "teacher", email: "t@dershane.test", specialtySubjectId: "s-1" }),
    );
    expect(update.body).toMatchObject({ email: "t@dershane.test", specialtySubjectId: "s-1" });
    expect(update.body).not.toHaveProperty("studentNumber");
  });
});

describe("classroomFormSchema", () => {
  it("checks name, grade and term format", () => {
    expect(
      classroomFormSchema.safeParse({ name: "12-A", gradeLevel: "12", term: "2026-2027" }).success,
    ).toBe(true);
    expect(
      classroomFormSchema.safeParse({ name: "", gradeLevel: "0", term: "2026" }).error?.issues,
    ).toHaveLength(3);
  });

  it("builds the create request", () => {
    expect(
      toCreateClassroom({ name: " 12-A ", gradeLevel: "12", term: "2026-2027" }, "b-1"),
    ).toEqual({ branchId: "b-1", name: "12-A", gradeLevel: 12, term: "2026-2027" });
  });
});
