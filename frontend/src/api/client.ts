import { type User } from "oidc-client-ts";
import createClient, { type Middleware } from "openapi-fetch";
import { getUserManager } from "@/auth/oidc";
import { currentPath } from "@/auth/returnPath";
import { getConfig } from "@/config";
import { toast } from "sonner";
import { ApiError, NETWORK_ERROR_CODE, isNetworkError, problemOf } from "./problem";
import type { paths as UserPaths } from "./schema/user";
import type { paths as AttendancePaths } from "./schema/attendance";
import type { paths as TaskPaths } from "./schema/task";
import type { paths as ExamPaths } from "./schema/exam-analysis";
import type { paths as NotificationPaths } from "./schema/notification";

// Request clones taken before fetch consumes the body, so a 401 can be retried once.
const retryable = new WeakMap<Request, Request>();

// Parallel 401s share one silent renew and trigger at most one redirect.
let silentRenew: Promise<User | null> | undefined;
let redirecting = false;

function renewOnce(): Promise<User | null> {
  silentRenew ??= getUserManager()
    .signinSilent()
    .finally(() => {
      silentRenew = undefined;
    });
  return silentRenew;
}

function redirectOnce() {
  if (redirecting) return;
  redirecting = true;
  getUserManager()
    .signinRedirect({ state: currentPath() })
    .catch((error) => {
      redirecting = false;
      console.warn("Sign-in redirect failed", error);
      // Keycloak down: without this the expired session just sits there silently.
      if (isNetworkError(error)) toast.error(problemOf(error).message, { id: NETWORK_ERROR_CODE });
    });
}

const authMiddleware: Middleware = {
  async onRequest({ request }) {
    const user = await getUserManager().getUser();
    if (user?.access_token) request.headers.set("Authorization", `Bearer ${user.access_token}`);
    request.headers.set("Accept-Language", "tr");
    retryable.set(request, request.clone());
    return request;
  },
  async onResponse({ request, response }) {
    if (response.status !== 401) return;
    const copy = retryable.get(request);
    retryable.delete(request);
    try {
      const user = await renewOnce();
      if (copy && user?.access_token) {
        copy.headers.set("Authorization", `Bearer ${user.access_token}`);
        const retried = await fetch(copy);
        if (retried.status !== 401) return retried;
      }
    } catch (error) {
      console.warn("Silent sign-in failed, redirecting to login", error);
    }
    redirectOnce();
  },
};

// All traffic goes through the gateway, never directly to a service.
export function createServiceClient<Paths extends object>() {
  // Spring binds page/size/sort as flat params; the generated `pageable` object must not become pageable[page].
  const client = createClient<Paths>({
    baseUrl: getConfig().apiBaseUrl,
    querySerializer: { object: { style: "form", explode: true } },
  });
  client.use(authMiddleware);
  return client;
}

export const userApi = createServiceClient<UserPaths>();
export const attendanceApi = createServiceClient<AttendancePaths>();
export const taskApi = createServiceClient<TaskPaths>();
export const examApi = createServiceClient<ExamPaths>();
export const notificationApi = createServiceClient<NotificationPaths>();

// Returns the data or throws ApiError so TanStack Query can handle failures uniformly.
export async function unwrap<T>(
  result: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await result;
  if (!response.ok) throw new ApiError(response.status, error);
  return data as T;
}
