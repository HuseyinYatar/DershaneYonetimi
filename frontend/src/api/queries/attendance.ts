import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { attendanceApi, unwrap } from "../client";
import type { TimetableSetting } from "@/lib/scheduleBoard";
import { isDraftMissing } from "@/lib/timetableDraft";
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

// Every active weekly schedule of the branch, for the automatic distribution (teachers are shared across classrooms).
// shortcut: one page of 1000; a branch has a few hundred schedules at most, page it if that ever changes.
export function useBranchSchedules() {
  return useQuery({
    queryKey: ["attendance", "schedules", "branch"],
    queryFn: () =>
      unwrap(
        attendanceApi.GET("/api/v1/class-schedules", {
          params: { query: { active: true, pageable: { page: 0, size: 1000 } } },
        }),
      ),
    select: (page) => page.content ?? [],
  });
}

// Lesson-hour grid of a branch (a super-admin has no branch of their own, so it is always passed).
// No local default while loading or on error: a wrong grid would show lessons on the wrong rows.
export function useTimetableSetting(branchId: string | undefined) {
  return useQuery({
    queryKey: ["attendance", "timetable-setting", branchId],
    enabled: !!branchId,
    queryFn: () =>
      unwrap(attendanceApi.GET("/api/v1/timetable-setting", { params: { query: { branchId } } })),
    select: (r): TimetableSetting => ({
      firstLessonStart: (r.firstLessonStart ?? "").slice(0, 5),
      lessonMinutes: r.lessonMinutes ?? 0,
      breakMinutes: r.breakMinutes ?? 0,
      lessonCount: r.lessonCount ?? 0,
      lunchLesson: r.lunchLesson ?? 0,
    }),
  });
}

// Writes refresh everything attendance-related; the screens are few and the data small.
function useAttendanceMutation<Variables, Result>(
  mutationFn: (variables: Variables) => Promise<Result>,
  successText: string | ((result: Result) => string) | undefined,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async (result) => {
      const text = typeof successText === "function" ? successText(result) : successText;
      if (text) toast.success(text);
      await queryClient.invalidateQueries({ queryKey: ["attendance"] });
    },
  });
}

export function useSaveTimetableSetting(branchId: string) {
  return useAttendanceMutation(
    (setting: TimetableSetting) =>
      unwrap(attendanceApi.PUT("/api/v1/timetable-setting", { body: { ...setting, branchId } })),
    "Ders saatleri kaydedildi.",
  );
}

// The branch's stored timetable draft; null when there is none (that 404 means "no draft", not a failure).
export function useCurrentDraft(branchId: string | undefined) {
  return useQuery({
    queryKey: ["attendance", "timetable-draft", branchId],
    enabled: !!branchId,
    retry: false,
    queryFn: async () => {
      try {
        return await unwrap(
          attendanceApi.GET("/api/v1/timetable-drafts/current", {
            params: { query: { branchId } },
          }),
        );
      } catch (error) {
        if (isDraftMissing(error)) return null;
        throw error;
      }
    },
  });
}

// A branch admin's branch comes from the token; "" means the caller has none and the service decides.
const branchQuery = (branchId: string) => ({ branchId: branchId || undefined });

// The service plans the week and stores the result; the caller decides what to tell the user.
export function useGenerateDraft(branchId: string) {
  return useAttendanceMutation(
    (days: NonNullable<Schemas["GenerateTimetableDraftRequest"]["days"]>) =>
      unwrap(
        attendanceApi.POST("/api/v1/timetable-drafts", {
          body: { ...branchQuery(branchId), days },
        }),
      ),
    undefined,
  );
}

// A merged box stands for several lessons: they move one by one (the caller orders them so none lands on
// a sibling), the first rejection stops the rest, and the data is refreshed either way.
export function useMoveDraftLessons(branchId: string) {
  const queryClient = useQueryClient();
  return useAttendanceMutation(
    async (moves: { lessonId: string; body: Schemas["MoveTimetableDraftLessonRequest"] }[]) => {
      try {
        for (const { lessonId, body } of moves) {
          await unwrap(
            attendanceApi.PUT("/api/v1/timetable-drafts/current/lessons/{lessonId}", {
              params: { path: { lessonId }, query: branchQuery(branchId) },
              body,
            }),
          );
        }
      } finally {
        await queryClient.invalidateQueries({ queryKey: ["attendance"] });
      }
    },
    undefined,
  );
}

export function useDeleteDraftLessons(branchId: string) {
  const queryClient = useQueryClient();
  return useAttendanceMutation(async (lessonIds: string[]) => {
    try {
      for (const lessonId of lessonIds) {
        await unwrap(
          attendanceApi.DELETE("/api/v1/timetable-drafts/current/lessons/{lessonId}", {
            params: { path: { lessonId }, query: branchQuery(branchId) },
          }),
        );
      }
    } finally {
      await queryClient.invalidateQueries({ queryKey: ["attendance"] });
    }
  }, undefined);
}

export function useDiscardDraft(branchId: string) {
  return useAttendanceMutation(
    () =>
      unwrap(
        attendanceApi.DELETE("/api/v1/timetable-drafts/current", {
          params: { query: branchQuery(branchId) },
        }),
      ),
    "Taslak atıldı.",
  );
}

// All or nothing on the service side: a conflict rejects the whole draft and writes nothing.
export function useCommitDraft(branchId: string) {
  return useAttendanceMutation(
    (validFrom: string) =>
      unwrap(
        attendanceApi.POST("/api/v1/timetable-drafts/current/commit", {
          params: { query: branchQuery(branchId) },
          body: { validFrom },
        }),
      ),
    (result) => `Taslak kaydedildi (${result.created ?? 0} ders).`,
  );
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

// Same call as useUpdateSchedule for several lessons at once (a merged box moves them together); one by
// one, one toast, refreshed even when one fails.
export function useMoveSchedules() {
  const queryClient = useQueryClient();
  return useAttendanceMutation(
    async (moves: { scheduleId: string; body: Schemas["UpdateScheduleRequest"] }[]) => {
      try {
        for (const { scheduleId, body } of moves) {
          await unwrap(
            attendanceApi.PUT("/api/v1/class-schedules/{scheduleId}", {
              params: { path: { scheduleId } },
              body,
            }),
          );
        }
      } finally {
        await queryClient.invalidateQueries({ queryKey: ["attendance"] });
      }
    },
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

export function useDeactivateSchedules() {
  const queryClient = useQueryClient();
  return useAttendanceMutation(async (scheduleIds: string[]) => {
    try {
      for (const scheduleId of scheduleIds) {
        await unwrap(
          attendanceApi.DELETE("/api/v1/class-schedules/{scheduleId}", {
            params: { path: { scheduleId } },
          }),
        );
      }
    } finally {
      await queryClient.invalidateQueries({ queryKey: ["attendance"] });
    }
  }, "Program pasife alındı.");
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
