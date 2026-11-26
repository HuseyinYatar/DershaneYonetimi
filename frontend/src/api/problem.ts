export interface ApiProblem {
  errorCode?: string;
  message: string;
}

const FALLBACK_MESSAGE = "Beklenmeyen bir hata oluştu.";
export const NETWORK_ERROR_CODE = "SERVICE_UNREACHABLE";
const NETWORK_MESSAGE = "Bağlantı kurulamadı, birazdan tekrar deneyin.";
const OFFLINE_MESSAGE = "İnternet bağlantınız yok.";
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

// fetch rejects with a TypeError when the server cannot be reached (connection refused, DNS, offline).
export function isNetworkError(error: unknown): boolean {
  return error instanceof TypeError;
}

// The browser knows when the device itself is offline; then the problem is not on our side.
export function isOffline(): boolean {
  return typeof navigator !== "undefined" && !navigator.onLine;
}

// Other non-API errors have no problem body.
export function problemOf(error: unknown): ApiProblem {
  if (error instanceof ApiError) return error.problem;
  if (isNetworkError(error)) {
    return {
      errorCode: NETWORK_ERROR_CODE,
      message: isOffline() ? OFFLINE_MESSAGE : NETWORK_MESSAGE,
    };
  }
  return { message: FALLBACK_MESSAGE };
}
