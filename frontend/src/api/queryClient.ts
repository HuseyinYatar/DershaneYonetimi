import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ApiError, NETWORK_ERROR_CODE, problemOf } from "./problem";

const notify = (error: unknown) => {
  // The 401 handler in the API client is already redirecting to login.
  if (error instanceof ApiError && error.status === 401) return;
  const problem = problemOf(error);
  // One toast while the server is down, not one per failed query.
  const isNetwork = problem.errorCode === NETWORK_ERROR_CODE;
  toast.error(problem.message, isNetwork ? { id: NETWORK_ERROR_CODE } : undefined);
};

export const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: notify }),
  mutationCache: new MutationCache({ onError: notify }),
  defaultOptions: {
    queries: {
      // A 4xx will not fix itself on retry.
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) &&
        failureCount < 2,
    },
  },
});
