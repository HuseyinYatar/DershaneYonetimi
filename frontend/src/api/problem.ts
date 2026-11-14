export interface ApiProblem {
  errorCode?: string;
  message: string;
}

const FALLBACK_MESSAGE = "Beklenmeyen bir hata oluştu.";
const RATE_LIMIT_MESSAGE = "Çok fazla istek gönderildi, biraz sonra tekrar deneyin.";

// Parses an RFC 9457 ProblemDetail body (application/problem+json). `detail` is already Turkish.
export function parseProblem(status: number, body: unknown): ApiProblem {
  const problem =
    typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
  const errorCode = typeof problem.errorCode === "string" ? problem.errorCode : undefined;
  if (typeof problem.detail === "string" && problem.detail.trim()) {
    return { errorCode, message: problem.detail };
  }
  return { errorCode, message: status === 429 ? RATE_LIMIT_MESSAGE : FALLBACK_MESSAGE };
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ApiProblem;

  constructor(status: number, body: unknown) {
    const problem = parseProblem(status, body);
    super(problem.message);
    this.status = status;
    this.problem = problem;
  }
}

// Network failures and other non-API errors have no problem body.
export function problemOf(error: unknown): ApiProblem {
  return error instanceof ApiError ? error.problem : { message: FALLBACK_MESSAGE };
}
