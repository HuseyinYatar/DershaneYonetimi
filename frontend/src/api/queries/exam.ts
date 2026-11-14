import { useQueries, useQuery } from "@tanstack/react-query";
import { examApi, unwrap } from "../client";

const TEN_MINUTES = 10 * 60_000;

export function useExamTypes() {
  return useQuery({
    queryKey: ["exam", "exam-types"],
    queryFn: () => unwrap(examApi.GET("/api/v1/exam-types")),
    staleTime: TEN_MINUTES,
  });
}

// Published results of one student, newest first.
export function useStudentResults(studentId: string) {
  return useQuery({
    queryKey: ["exam", "results", studentId],
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/students/{student_id}/results", {
          params: { path: { student_id: studentId } },
        }),
      ),
  });
}

export function useStudentResult(studentId: string, mockExamId: string | undefined) {
  return useQuery({
    queryKey: ["exam", "results", studentId, mockExamId],
    enabled: !!mockExamId,
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/students/{student_id}/results/{mock_exam_id}", {
          params: { path: { student_id: studentId, mock_exam_id: mockExamId! } },
        }),
      ),
  });
}

export function useProgress(studentId: string, examTypeId: string | undefined) {
  return useQuery({
    queryKey: ["exam", "progress", studentId, examTypeId],
    enabled: !!examTypeId,
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/students/{student_id}/progress", {
          params: { path: { student_id: studentId }, query: { examTypeId: examTypeId! } },
        }),
      ),
  });
}

export function useWeakTopics(studentId: string) {
  return useQuery({
    queryKey: ["exam", "weak-topics", studentId],
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/students/{student_id}/weak-topics", {
          params: { path: { student_id: studentId } },
        }),
      ),
  });
}

// subjectId -> name across every exam type (weak topics are not tied to one exam).
export function useExamSubjectNames() {
  const types = useExamTypes();
  const results = useQueries({
    queries: (types.data ?? []).map((type) => ({
      queryKey: ["exam", "subjects", type.id],
      staleTime: TEN_MINUTES,
      queryFn: () =>
        unwrap(
          examApi.GET("/api/v1/exam-types/{exam_type_id}/subjects", {
            params: { path: { exam_type_id: type.id } },
          }),
        ),
    })),
  });
  const names = new Map<string, string>();
  for (const result of results) {
    for (const subject of result.data ?? []) names.set(subject.id, subject.name);
  }
  return names;
}

// subjectId -> ids of its topics; lets the result page group topic rows by subject.
export function useSubjectTopicIds(subjectIds: string[]) {
  const results = useQueries({
    queries: subjectIds.map((subjectId) => ({
      queryKey: ["exam", "topics", subjectId],
      staleTime: TEN_MINUTES,
      queryFn: () =>
        unwrap(
          examApi.GET("/api/v1/subjects/{subject_id}/topics", {
            params: { path: { subject_id: subjectId } },
          }),
        ),
    })),
  });
  const bySubject = new Map<string, Set<string>>();
  subjectIds.forEach((id, i) =>
    bySubject.set(id, new Set((results[i]?.data ?? []).map((t) => t.id))),
  );
  return { bySubject, isPending: results.some((r) => r.isPending) };
}
