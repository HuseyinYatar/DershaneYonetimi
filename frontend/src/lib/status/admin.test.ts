import { describe, expect, it } from "vitest";
import {
  activeBadge,
  isMatchable,
  jobBadge,
  jobIsRunning,
  matchBadge,
  unmatchedReasonLabel,
} from "./admin";

describe("admin status mappings", () => {
  it("labels import jobs and knows which ones are still running", () => {
    expect(jobBadge("FAILED")).toEqual({ tone: "danger", label: "Başarısız" });
    expect(jobIsRunning("QUEUED")).toBe(true);
    expect(jobIsRunning("PROCESSING")).toBe(true);
    expect(jobIsRunning("COMPLETED")).toBe(false);
    expect(jobIsRunning(undefined)).toBe(false);
  });

  it("lets only student-related reasons be matched to a student", () => {
    expect(isMatchable("STUDENT_NOT_FOUND")).toBe(true);
    expect(isMatchable("DUPLICATE_STUDENT")).toBe(true);
    expect(isMatchable("INVALID_BOOKLET")).toBe(false);
    expect(isMatchable("MALFORMED_LINE")).toBe(false);
    expect(unmatchedReasonLabel("MALFORMED_LINE")).toBe("Bozuk satır");
  });

  it("labels match and account states", () => {
    expect(matchBadge("IGNORED").label).toBe("Yok sayıldı");
    expect(activeBadge(true).tone).toBe("success");
    expect(activeBadge(false).label).toBe("Pasif");
  });
});
