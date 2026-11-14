import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { taskApi, unwrap } from "../client";
import type { components } from "../schema/task";

type CreateTaskRequest = components["schemas"]["CreateTaskRequest"];
type UpdateTaskRequest = components["schemas"]["UpdateTaskRequest"];
type EvaluateRequest = components["schemas"]["EvaluateAssignmentRequest"];
type AssignmentStatus = NonNullable<components["schemas"]["AssignmentSummaryResponse"]["status"]>;

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

export function useTasks(teacherId: string | undefined) {
  return useQuery({
    queryKey: ["task", "tasks", teacherId],
    enabled: !!teacherId,
    queryFn: () =>
      unwrap(
        taskApi.GET("/api/v1/tasks", {
          params: { query: { teacherId, pageable: { page: 0, size: 100 } } },
        }),
      ),
    select: (page) => page.content ?? [],
  });
}

export function useTask(taskId: string) {
  return useQuery({
    queryKey: ["task", "task", taskId],
    queryFn: () => unwrap(taskApi.GET("/api/v1/tasks/{taskId}", { params: { path: { taskId } } })),
  });
}

// The key makes a repeated request (double click, retry) return the first response instead of a second task.
export function useCreateTask() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ body, idempotencyKey }: { body: CreateTaskRequest; idempotencyKey: string }) =>
      unwrap(
        taskApi.POST("/api/v1/tasks", {
          params: { header: { "Idempotency-Key": idempotencyKey } },
          body,
        }),
      ),
    onSuccess: async () => {
      toast.success("Görev oluşturuldu.");
      await queryClient.invalidateQueries({ queryKey: ["task"] });
    },
  });
}

export function useUpdateTask(taskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateTaskRequest) =>
      unwrap(taskApi.PUT("/api/v1/tasks/{taskId}", { params: { path: { taskId } }, body })),
    onSuccess: async () => {
      toast.success("Görev güncellendi.");
      await queryClient.invalidateQueries({ queryKey: ["task"] });
    },
  });
}

export function useDeactivateTask(taskId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      unwrap(taskApi.POST("/api/v1/tasks/{taskId}/deactivate", { params: { path: { taskId } } })),
    onSuccess: async () => {
      toast.success("Görev pasife alındı.");
      await queryClient.invalidateQueries({ queryKey: ["task"] });
    },
  });
}

export function useTaskAssignments(taskId: string, status?: AssignmentStatus) {
  return useQuery({
    queryKey: ["task", "task-assignments", taskId, status],
    queryFn: () =>
      unwrap(
        taskApi.GET("/api/v1/tasks/{taskId}/assignments", {
          params: { path: { taskId }, query: { status, pageable: { page: 0, size: 100 } } },
        }),
      ),
    select: (page) => page.content ?? [],
  });
}

// Newest page of submissions waiting for a grade; `page.totalElements` is the full count.
export function useSubmittedAssignments(size: number) {
  return useQuery({
    queryKey: ["task", "submitted", size],
    queryFn: () =>
      unwrap(
        taskApi.GET("/api/v1/task-assignments", {
          params: { query: { status: "SUBMITTED", pageable: { page: 0, size } } },
        }),
      ),
  });
}

// PUT is idempotent, so no Idempotency-Key header.
export function useEvaluateAssignment(assignmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: EvaluateRequest) =>
      unwrap(
        taskApi.PUT("/api/v1/task-assignments/{assignmentId}/evaluation", {
          params: { path: { assignmentId } },
          body,
        }),
      ),
    onSuccess: async () => {
      toast.success("Değerlendirme kaydedildi.");
      await queryClient.invalidateQueries({ queryKey: ["task"] });
    },
  });
}
