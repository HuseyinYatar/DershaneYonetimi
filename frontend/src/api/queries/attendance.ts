import { useQuery } from "@tanstack/react-query";
import { attendanceApi, unwrap } from "../client";

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
