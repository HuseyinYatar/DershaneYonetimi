import { describe, expect, it } from "vitest";
import {
  describeSchedule,
  evaluationSchema,
  taskFormSchema,
  toCreateRequest,
  toEvaluationRequest,
  type TaskFormValues,
} from "./taskForm";
import { dateKey } from "./week";

const day = (offset: number) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return dateKey(d);
};

const valid: TaskFormValues = {
  title: "Problem çözümü",
  description: "",
  targetType: "CLASSROOM",
  classroomId: "c1",
  studentId: "",
  recurrence: "ONCE",
  recurrenceDays: [],
  startDate: day(1),
  endDate: day(2),
  dueTime: "18:00",
};

const issues = (values: TaskFormValues) =>
  (taskFormSchema.safeParse(values).error?.issues ?? []).map((i) => i.path[0]);

describe("taskFormSchema", () => {
  it("accepts a valid one-off task", () => {
    expect(taskFormSchema.safeParse(valid).success).toBe(true);
  });

  it("needs a student for a student target", () => {
    expect(issues({ ...valid, targetType: "STUDENT" })).toEqual(["studentId"]);
  });

  it("needs weekdays only for weekly tasks", () => {
    expect(issues({ ...valid, recurrence: "WEEKLY", endDate: "" })).toEqual(["recurrenceDays"]);
    expect(
      issues({ ...valid, recurrence: "WEEKLY", endDate: "", recurrenceDays: ["MONDAY"] }),
    ).toEqual([]);
  });

  it("needs an end date for a one-off task but not a repeating one", () => {
    expect(issues({ ...valid, endDate: "" })).toEqual(["endDate"]);
    expect(issues({ ...valid, recurrence: "DAILY", endDate: "" })).toEqual([]);
  });

  it("rejects a start in the past and an end before the start", () => {
    expect(issues({ ...valid, startDate: day(-1), endDate: day(2) })).toEqual(["startDate"]);
    expect(issues({ ...valid, startDate: day(3), endDate: day(2) })).toEqual(["endDate"]);
  });

  it("rejects a one-off task whose due time has passed", () => {
    const today = day(0);
    expect(issues({ ...valid, startDate: today, endDate: today, dueTime: "00:00" })).toEqual([
      "dueTime",
    ]);
  });
});

describe("toCreateRequest", () => {
  it("targets the classroom or the picked student and sends weekdays only when weekly", () => {
    expect(toCreateRequest(valid)).toMatchObject({ targetId: "c1", recurrenceDays: undefined });
    expect(
      toCreateRequest({
        ...valid,
        targetType: "STUDENT",
        studentId: "s1",
        recurrenceDays: ["MONDAY"],
      }),
    ).toMatchObject({ targetId: "s1", recurrenceDays: undefined });
    expect(
      toCreateRequest({ ...valid, recurrence: "WEEKLY", endDate: "", recurrenceDays: ["MONDAY"] }),
    ).toMatchObject({ recurrenceDays: ["MONDAY"], endDate: undefined });
  });
});

describe("evaluationSchema", () => {
  const check = (score: string, feedback: string) =>
    evaluationSchema.safeParse({ score, feedback }).success;

  it("needs a score or feedback", () => {
    expect(check("", "  ")).toBe(false);
    expect(check("80", "")).toBe(true);
    expect(check("", "Güzel")).toBe(true);
  });

  it("keeps the score a whole number between 0 and 100", () => {
    expect(check("0", "")).toBe(true);
    expect(check("100", "")).toBe(true);
    expect(check("101", "")).toBe(false);
    expect(check("-1", "")).toBe(false);
    expect(check("7.5", "")).toBe(false);
  });

  it("builds the request with empty parts left out", () => {
    expect(toEvaluationRequest({ score: " 85 ", feedback: " " })).toEqual({
      score: 85,
      feedback: undefined,
    });
  });
});

describe("describeSchedule", () => {
  it("describes each recurrence", () => {
    expect(
      describeSchedule({
        recurrence: "ONCE",
        startDate: "2026-11-14",
        endDate: "2026-11-16",
        dueTime: "23:59:00",
      }),
    ).toContain("23:59");
    expect(
      describeSchedule({ recurrence: "DAILY", startDate: "2026-11-14", dueTime: "18:00:00" }),
    ).toContain("Her gün");
    expect(
      describeSchedule({
        recurrence: "WEEKLY",
        recurrenceDays: ["MONDAY", "WEDNESDAY"],
        startDate: "2026-11-14",
        endDate: "2026-12-20",
        dueTime: "18:00:00",
      }),
    ).toContain("Pzt, Çar");
  });
});
