import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { jobIsRunning } from "@/lib/status/admin";
import { examApi, unwrap } from "../client";
import { ApiError } from "../problem";
import type { components } from "../schema/exam-analysis";

type Schemas = components["schemas"];
type MatchStatus = Schemas["MatchStatus"];

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

// Mock exams of a branch (teachers may read them; uploads and publishing are admin-only).
export function useMockExams(branchId: string | undefined) {
  return useQuery({
    queryKey: ["exam", "mock-exams", branchId],
    enabled: !!branchId,
    queryFn: () =>
      unwrap(examApi.GET("/api/v1/mock-exams", { params: { query: { branchId: branchId! } } })),
  });
}

export function useMockExam(mockExamId: string) {
  return useQuery({
    queryKey: ["exam", "mock-exam", mockExamId],
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/mock-exams/{mock_exam_id}", {
          params: { path: { mock_exam_id: mockExamId } },
        }),
      ),
  });
}

export function useExamResults(mockExamId: string) {
  return useQuery({
    queryKey: ["exam", "exam-results", mockExamId],
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/mock-exams/{mock_exam_id}/results", {
          params: { path: { mock_exam_id: mockExamId } },
        }),
      ),
  });
}

export function useTopicAnalysis(mockExamId: string) {
  return useQuery({
    queryKey: ["exam", "topic-analysis", mockExamId],
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/mock-exams/{mock_exam_id}/topic-analysis", {
          params: { path: { mock_exam_id: mockExamId } },
        }),
      ),
  });
}

// ---- Exam management (role admin) ----

export function useExamSubjects(examTypeId: string | undefined) {
  return useQuery({
    queryKey: ["exam", "subjects", examTypeId],
    enabled: !!examTypeId,
    staleTime: TEN_MINUTES,
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/exam-types/{exam_type_id}/subjects", {
          params: { path: { exam_type_id: examTypeId! } },
        }),
      ),
  });
}

// Topics per subject; shares cache keys with useSubjectTopicIds.
export function useSubjectTopics(subjectIds: string[]) {
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
  const topics = new Map<string, Schemas["TopicRead"][]>();
  subjectIds.forEach((id, i) => topics.set(id, results[i]?.data ?? []));
  return { topics, isPending: results.some((r) => r.isPending) };
}

export function useOpticalFormats() {
  return useQuery({
    queryKey: ["exam", "optical-formats"],
    staleTime: TEN_MINUTES,
    queryFn: () => unwrap(examApi.GET("/api/v1/optical-formats")),
  });
}

export function useAnswerKey(mockExamId: string) {
  return useQuery({
    queryKey: ["exam", "answer-key", mockExamId],
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/mock-exams/{mock_exam_id}/answer-key", {
          params: { path: { mock_exam_id: mockExamId } },
        }),
      ),
  });
}

export function useStatistics(mockExamId: string) {
  return useQuery({
    queryKey: ["exam", "statistics", mockExamId],
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/mock-exams/{mock_exam_id}/statistics", {
          params: { path: { mock_exam_id: mockExamId } },
        }),
      ),
  });
}

// Polls every 2 s while the worker has not finished the job.
export function useImportJob(jobId: string | null) {
  return useQuery({
    queryKey: ["exam", "import-job", jobId],
    enabled: !!jobId,
    queryFn: () =>
      unwrap(examApi.GET("/api/v1/import-jobs/{job_id}", { params: { path: { job_id: jobId! } } })),
    refetchInterval: (query) => (jobIsRunning(query.state.data?.status) ? 2000 : false),
  });
}

export function useUnmatchedRows(jobId: string | null, status: MatchStatus | undefined) {
  return useQuery({
    queryKey: ["exam", "unmatched-rows", jobId, status],
    enabled: !!jobId,
    queryFn: () =>
      unwrap(
        examApi.GET("/api/v1/import-jobs/{job_id}/unmatched-rows", {
          params: { path: { job_id: jobId! }, query: { status } },
        }),
      ),
  });
}

// The setting is missing until an admin saves one; that is an ordinary state, not an error (no toast).
export function useWeakTopicSetting(branchId: string | undefined) {
  return useQuery({
    queryKey: ["exam", "weak-topic-setting", branchId],
    enabled: !!branchId,
    queryFn: async () => {
      try {
        return await unwrap(
          examApi.GET("/api/v1/branches/{branch_id}/weak-topic-setting", {
            params: { path: { branch_id: branchId! } },
          }),
        );
      } catch (error) {
        if (error instanceof ApiError && error.problem.errorCode === "WEAK_TOPIC_SETTING_MISSING") {
          return null;
        }
        throw error;
      }
    },
  });
}

// Writes refresh all exam data: a key, a match or a statistic changes results, ranks and scores.
function useExamMutation<Variables, Result>(
  mutationFn: (variables: Variables) => Promise<Result>,
  successText: string,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async () => {
      toast.success(successText);
      await queryClient.invalidateQueries({ queryKey: ["exam"] });
    },
  });
}

export function useCreateMockExam() {
  return useExamMutation(
    (body: Schemas["MockExamCreate"]) => unwrap(examApi.POST("/api/v1/mock-exams", { body })),
    "Deneme oluşturuldu.",
  );
}

export function useReplaceAnswerKey(mockExamId: string) {
  return useExamMutation(
    (items: Schemas["AnswerKeyItemIn"][]) =>
      unwrap(
        examApi.PUT("/api/v1/mock-exams/{mock_exam_id}/answer-key", {
          params: { path: { mock_exam_id: mockExamId } },
          body: { items },
        }),
      ),
    "Cevap anahtarı kaydedildi.",
  );
}

export function useCancelQuestion(mockExamId: string) {
  return useExamMutation(
    (itemId: string) =>
      unwrap(
        examApi.POST("/api/v1/mock-exams/{mock_exam_id}/answer-key/{item_id}/cancel", {
          params: { path: { mock_exam_id: mockExamId, item_id: itemId } },
        }),
      ),
    "Soru iptal edildi, sonuçlar yeniden hesaplandı.",
  );
}

export function useUploadOpticalFile(mockExamId: string) {
  return useExamMutation(
    ({ file, opticalFormatId }: { file: File; opticalFormatId: string }) =>
      unwrap(
        examApi.POST("/api/v1/mock-exams/{mock_exam_id}/import-jobs", {
          params: { path: { mock_exam_id: mockExamId } },
          // The typed body only satisfies the schema; the serializer builds the real multipart body.
          body: { file: "", opticalFormatId },
          bodySerializer: () => {
            const form = new FormData();
            form.append("file", file);
            form.append("opticalFormatId", opticalFormatId);
            return form;
          },
        }),
      ),
    "Dosya yüklendi, işleniyor.",
  );
}

export function usePublishExam(mockExamId: string) {
  return useExamMutation(
    () =>
      unwrap(
        examApi.POST("/api/v1/mock-exams/{mock_exam_id}/publish", {
          params: { path: { mock_exam_id: mockExamId } },
        }),
      ),
    "Sonuçlar açıklandı.",
  );
}

export function useSetNationalStatistics(mockExamId: string) {
  return useExamMutation(
    (items: Schemas["NationalStatisticItem"][]) =>
      unwrap(
        examApi.PUT("/api/v1/mock-exams/{mock_exam_id}/statistics/national", {
          params: { path: { mock_exam_id: mockExamId } },
          body: { items },
        }),
      ),
    "Türkiye geneli istatistikler kaydedildi.",
  );
}

export function useMatchRow() {
  return useExamMutation(
    ({ rowId, studentId }: { rowId: string; studentId: string }) =>
      unwrap(
        examApi.POST("/api/v1/unmatched-rows/{row_id}/match", {
          params: { path: { row_id: rowId } },
          body: { studentId },
        }),
      ),
    "Satır öğrenciyle eşleştirildi.",
  );
}

export function useIgnoreRow() {
  return useExamMutation(
    (rowId: string) =>
      unwrap(
        examApi.POST("/api/v1/unmatched-rows/{row_id}/ignore", {
          params: { path: { row_id: rowId } },
        }),
      ),
    "Satır yok sayıldı.",
  );
}

export function useUpdateWeakTopicSetting(branchId: string) {
  return useExamMutation(
    (body: Schemas["WeakTopicSettingUpdate"]) =>
      unwrap(
        examApi.PUT("/api/v1/branches/{branch_id}/weak-topic-setting", {
          params: { path: { branch_id: branchId } },
          body,
        }),
      ),
    "Zayıf konu ayarı kaydedildi.",
  );
}
