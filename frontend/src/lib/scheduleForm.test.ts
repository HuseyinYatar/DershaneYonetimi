import { describe, expect, it } from "vitest";
import {
  scheduleFormSchema,
  sessionFormSchema,
  toCreateSchedule,
  toUpdateSchedule,
  type ScheduleFormValues,
} from "./scheduleForm";

const schedule: ScheduleFormValues = {
  classroomId: "c-1",
  teacherId: "t-1",
  subjectId: "s-1",
  dayOfWeek: "MONDAY",
  startsAt: "09:00",
  endsAt: "10:30",
  validFrom: "2026-11-16",
  validTo: "",
};

const issuePaths = (result: { success: boolean; error?: { issues: { path: PropertyKey[] }[] } }) =>
  result.error?.issues.map((i) => String(i.path[0])) ?? [];

describe("scheduleFormSchema", () => {
  it("accepts a valid open-ended schedule", () => {
    expect(scheduleFormSchema.safeParse(schedule).success).toBe(true);
  });

  it("rejects an end time that is not after the start time", () => {
    expect(issuePaths(scheduleFormSchema.safeParse({ ...schedule, endsAt: "09:00" }))).toEqual([
      "endsAt",
    ]);
  });

  it("rejects an end date before the start date", () => {
    expect(
      issuePaths(scheduleFormSchema.safeParse({ ...schedule, validTo: "2026-11-01" })),
    ).toEqual(["validTo"]);
  });

  it("requires class, teacher and subject", () => {
    const result = scheduleFormSchema.safeParse({
      ...schedule,
      classroomId: "",
      teacherId: "",
      subjectId: "",
    });
    expect(issuePaths(result)).toEqual(["classroomId", "teacherId", "subjectId"]);
  });
});

describe("request builders", () => {
  it("leaves the open end date out and keeps the classroom only on create", () => {
    expect(toCreateSchedule(schedule, "b-1")).toMatchObject({
      branchId: "b-1",
      classroomId: "c-1",
      validTo: undefined,
    });
    expect(toUpdateSchedule(schedule)).not.toHaveProperty("classroomId");
  });
});

describe("sessionFormSchema", () => {
  it("applies the same time rule", () => {
    const base = {
      classroomId: "c-1",
      teacherId: "t-1",
      subjectId: "s-1",
      sessionDate: "2026-11-20",
      startsAt: "18:00",
      endsAt: "17:00",
    };
    expect(issuePaths(sessionFormSchema.safeParse(base))).toEqual(["endsAt"]);
    expect(sessionFormSchema.safeParse({ ...base, endsAt: "19:00" }).success).toBe(true);
  });
});
