import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { taskApi, unwrap } from "../client";

// shortcut: the list is the newest 100 assignments (the server orders by due date, descending);
// counts and week filtering happen client-side. Upgrade to server-side counts past 100 rows.
export function useAssignments(studentId: string | undefined) {
  return useQuery({
    queryKey: ["task", "assignments", studentId],
    queryFn: () =>
      unwrap(
        taskApi.GET("/api/v1/task-assignments", {
          params: { query: { studentId, pageable: { page: 0, size: 100 } } },
        }),
      ),
    select: (page) => page.content ?? [],
  });
}

export function useAssignment(assignmentId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ["task", "assignment", assignmentId],
    enabled: enabled && !!assignmentId,
    queryFn: () =>
      unwrap(
        taskApi.GET("/api/v1/task-assignments/{assignmentId}", {
          params: { path: { assignmentId: assignmentId! } },
        }),
      ),
  });
}

// PUT is idempotent, so no Idempotency-Key header.
export function useSubmitAssignment(assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (description: string) =>
      unwrap(
        taskApi.PUT("/api/v1/task-assignments/{assignmentId}/submission", {
          params: { path: { assignmentId } },
          body: { description, fileIds: [] },
        }),
      ),
    onSuccess: async () => {
      toast.success("Teslimin kaydedildi.");
      await queryClient.invalidateQueries({ queryKey: ["task"] });
    },
  });
}
