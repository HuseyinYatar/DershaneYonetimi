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
export const sessionsQuery = (teacherId: string | undefined, range: Required<DateRange>) => ({
  queryKey: ["attendance", "sessions", teacherId, range],
  queryFn: () =>
    unwrap(
      attendanceApi.GET("/api/v1/class-sessions", {
        params: { query: { teacherId, ...range, pageable: { page: 0, size: 100 } } },
      }),
    ),
});

export function useSessions(teacherId: string | undefined, range: Required<DateRange>) {
  return useQuery({
    ...sessionsQuery(teacherId, range),
    enabled: !!teacherId,
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

// `reason` is only for an admin taking attendance after the session day ended.
export function useTakeAttendance(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ entries, reason }: { entries: Entries; reason?: string }) =>
      unwrap(
        attendanceApi.POST("/api/v1/class-sessions/{sessionId}/attendance", {
          params: { path: { sessionId } },
          body: { entries, reason },
        }),
      ),
    onSuccess: async () => {
      toast.success("Yoklama kaydedildi.");
      await queryClient.invalidateQueries({ queryKey: ["attendance"] });
    },
  });
}

type Schemas = components["schemas"];
type ExcuseStatus = NonNullable<Schemas["ExcuseResponse"]["status"]>;

// Branch admins: every session of the branch in a date range (the service defaults the branch).
export const branchSessionsQuery = (range: Required<DateRange>) => ({
  queryKey: ["attendance", "branch-sessions", range],
  queryFn: () =>
    unwrap(
      attendanceApi.GET("/api/v1/class-sessions", {
        params: { query: { ...range, pageable: { page: 0, size: 100 } } },
      }),
    ),
});

export function useBranchSessions(range: Required<DateRange>, enabled = true) {
  return useQuery({
    ...branchSessionsQuery(range),
    enabled,
    select: (page) => page.content ?? [],
  });
}

export function useSchedules(active: boolean | undefined, page: number, size: number) {
  return useQuery({
    queryKey: ["attendance", "schedules", active, page, size],
    queryFn: () =>
      unwrap(
        attendanceApi.GET("/api/v1/class-schedules", {
          params: { query: { active, pageable: { page, size } } },
        }),
      ),
    placeholderData: (previous) => previous,
  });
}

// Writes refresh everything attendance-related; the screens are few and the data small.
function useAttendanceMutation<Variables, Result>(
  mutationFn: (variables: Variables) => Promise<Result>,
  successText: string,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async () => {
      toast.success(successText);
      await queryClient.invalidateQueries({ queryKey: ["attendance"] });
    },
  });
}

export function useCreateSchedule() {
  return useAttendanceMutation(
    ({
      body,
      idempotencyKey,
    }: {
      body: Schemas["CreateScheduleRequest"];
      idempotencyKey: string;
    }) =>
      unwrap(
        attendanceApi.POST("/api/v1/class-schedules", {
          params: { header: { "Idempotency-Key": idempotencyKey } },
          body,
        }),
      ),
    "Haftalık program oluşturuldu.",
  );
}

export function useUpdateSchedule(scheduleId: string) {
  return useAttendanceMutation(
    (body: Schemas["UpdateScheduleRequest"]) =>
      unwrap(
        attendanceApi.PUT("/api/v1/class-schedules/{scheduleId}", {
          params: { path: { scheduleId } },
          body,
        }),
      ),
    "Program güncellendi.",
  );
}

// "Deleting" a schedule deactivates it; the sessions already generated stay.
export function useDeactivateSchedule() {
  return useAttendanceMutation(
    (scheduleId: string) =>
      unwrap(
        attendanceApi.DELETE("/api/v1/class-schedules/{scheduleId}", {
          params: { path: { scheduleId } },
        }),
      ),
    "Program pasife alındı.",
  );
}

export function useCreateSession() {
  return useAttendanceMutation(
    ({ body, idempotencyKey }: { body: Schemas["CreateSessionRequest"]; idempotencyKey: string }) =>
      unwrap(
        attendanceApi.POST("/api/v1/class-sessions", {
          params: { header: { "Idempotency-Key": idempotencyKey } },
          body,
        }),
      ),
    "Ders oluşturuldu.",
  );
}

export function useCancelSession(sessionId: string) {
  return useAttendanceMutation(
    () =>
      unwrap(
        attendanceApi.POST("/api/v1/class-sessions/{sessionId}/cancel", {
          params: { path: { sessionId } },
        }),
      ),
    "Ders iptal edildi.",
  );
}

export function useReassignTeacher(sessionId: string) {
  return useAttendanceMutation(
    (teacherId: string) =>
      unwrap(
        attendanceApi.PUT("/api/v1/class-sessions/{sessionId}/teacher", {
          params: { path: { sessionId } },
          body: { teacherId },
        }),
      ),
    "Derse giren öğretmen değiştirildi.",
  );
}

export function useExcuses(status: ExcuseStatus | undefined, page: number, size: number) {
  return useQuery({
    queryKey: ["attendance", "excuses", status, page, size],
    queryFn: () =>
      unwrap(
        attendanceApi.GET("/api/v1/excuses", {
          params: { query: { status, pageable: { page, size } } },
        }),
      ),
    placeholderData: (previous) => previous,
  });
}

export function useDecideExcuse() {
  return useAttendanceMutation(
    ({ excuseId, approve, note }: { excuseId: string; approve: boolean; note?: string }) =>
      unwrap(
        attendanceApi.POST(
          approve ? "/api/v1/excuses/{excuseId}/approve" : "/api/v1/excuses/{excuseId}/reject",
          { params: { path: { excuseId } }, body: { note } },
        ),
      ),
    "Karar kaydedildi.",
  );
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
