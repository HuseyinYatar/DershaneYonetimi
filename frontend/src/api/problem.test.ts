import { describe, expect, it } from "vitest";
import { ApiError, parseProblem, problemOf } from "./problem";

const FALLBACK = "Beklenmeyen bir hata oluştu.";

describe("parseProblem", () => {
  it("uses errorCode and detail from a ProblemDetail body", () => {
    expect(
      parseProblem(409, { errorCode: "STUDENT_NUMBER_TAKEN", detail: "Numara kullanımda." }),
    ).toEqual({ errorCode: "STUDENT_NUMBER_TAKEN", message: "Numara kullanımda." });
  });

  it("falls back to a generic Turkish message without detail", () => {
    expect(parseProblem(500, {})).toEqual({ errorCode: undefined, message: FALLBACK });
    expect(parseProblem(500, undefined).message).toBe(FALLBACK);
    expect(parseProblem(500, { detail: "  " }).message).toBe(FALLBACK);
  });

  it("explains 429 without detail but prefers the server detail", () => {
    expect(parseProblem(429, undefined).message).toBe(
      "Çok fazla istek gönderildi, biraz sonra tekrar deneyin.",
    );
    expect(parseProblem(429, { detail: "Yavaşla." }).message).toBe("Yavaşla.");
  });
});

describe("problemOf", () => {
  it("unwraps ApiError and hides other errors behind the generic message", () => {
    expect(problemOf(new ApiError(404, { errorCode: "NOT_FOUND", detail: "Yok." }))).toEqual({
      errorCode: "NOT_FOUND",
      message: "Yok.",
    });
    expect(problemOf(new TypeError("Failed to fetch")).message).toBe(FALLBACK);
  });
});
