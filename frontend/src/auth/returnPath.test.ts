import { describe, expect, it } from "vitest";
import { returnPath } from "./returnPath";

describe("returnPath", () => {
  it("keeps same-origin paths with query and hash", () => {
    expect(returnPath("/students/s-1/results/e-1")).toBe("/students/s-1/results/e-1");
    expect(returnPath("/tasks?status=ASSIGNED#top")).toBe("/tasks?status=ASSIGNED#top");
  });

  it("rejects open-redirect attempts", () => {
    expect(returnPath("//evil.com")).toBe("/");
    expect(returnPath("/\\evil.com")).toBe("/");
    expect(returnPath("https://evil.com")).toBe("/");
    expect(returnPath("/\t/evil.com")).toBe("/");
    expect(returnPath("/\n/evil.com")).toBe("/");
  });

  it("falls back to the home page for missing or non-string state", () => {
    expect(returnPath(undefined)).toBe("/");
    expect(returnPath(null)).toBe("/");
    expect(returnPath({ path: "/tasks" })).toBe("/");
    expect(returnPath(42)).toBe("/");
  });
});
