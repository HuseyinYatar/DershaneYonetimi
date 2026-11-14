import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { attendanceApi, unwrap } from "../client";
import type { components } from "../schema/attendance";

export interface DateRange {
  from?: string;
  to?: string;
}

export function useAttendanceSummary(studentId: string | undefined, range: DateRange = {}) {
  return useQuery({
    queryKey: ["attendance", "summary", studentId, range],
    queryFn: () =>
      unwrap(
        attendanceApi.GET("/api/v1/attendance-summaries", {
          params: { query: { studentId, ...range } },
        }),
      ),
  });
}

export function useAttendanceRecords(studentId: string | undefined, range: DateRange = {}) {
  return useQuery({
    queryKey: ["attendance", "records", studentId, range],
    queryFn: () =>
      unwrap(
        attendanceApi.GET("/api/v1/attendance-records", {
          params: { query: { studentId, ...range, pageable: { page: 0, size: 100 } } },
        }),
      ),
    select: (page) => page.content ?? [],
  });
}

type Entries = components["schemas"]["AttendanceEntryRequest"][];
type CorrectRequest = components["schemas"]["CorrectRecordRequest"];

// Sessions of a date range; the service already limits teachers to their own sessions.
export function useSessions(teacherId: string | undefined, range: Required<DateRange>) {
  return useQuery({
    queryKey: ["attendance", "sessions", teacherId, range],
    enabled: !!teacherId,
    queryFn: () =>
      unwrap(
        attendanceApi.GET("/api/v1/class-sessions", {
          params: { query: { teacherId, ...range, pageable: { page: 0, size: 100 } } },
        }),
      ),
    select: (page) => page.content ?? [],
  });
}

export function useSession(sessionId: string) {
  return useQuery({
    queryKey: ["attendance", "session", sessionId],
    queryFn: () =>
      unwrap(
        attendanceApi.GET("/api/v1/class-sessions/{sessionId}", {
          params: { path: { sessionId } },
        }),
      ),
  });
}

export function useRoster(sessionId: string, enabled = true) {
  return useQuery({
    queryKey: ["attendance", "roster", sessionId],
    enabled,
    queryFn: () =>
      unwrap(
        attendanceApi.GET("/api/v1/class-sessions/{sessionId}/roster", {
          params: { path: { sessionId } },
        }),
      ),
  });
}

export function useTakeAttendance(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (entries: Entries) =>
      unwrap(
        attendanceApi.POST("/api/v1/class-sessions/{sessionId}/attendance", {
          params: { path: { sessionId } },
          body: { entries },
        }),
      ),
    onSuccess: async () => {
      toast.success("Yoklama kaydedildi.");
      await queryClient.invalidateQueries({ queryKey: ["attendance"] });
    },
  });
}

export function useCorrectRecord(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ studentId, ...body }: CorrectRequest & { studentId: string }) =>
      unwrap(
        attendanceApi.PUT("/api/v1/class-sessions/{sessionId}/records/{studentId}", {
          params: { path: { sessionId, studentId } },
          body,
        }),
      ),
    onSuccess: async () => {
      toast.success("Yoklama düzeltildi.");
      await queryClient.invalidateQueries({ queryKey: ["attendance"] });
    },
  });
}
