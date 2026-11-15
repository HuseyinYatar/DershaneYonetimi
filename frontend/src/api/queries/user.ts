import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import type { CreateCall, UpdateCall, UserKind } from "@/lib/userForm";
import { unwrap, userApi } from "../client";
import { ApiError } from "../problem";
import type { components } from "../schema/user";

const FIVE_MINUTES = 5 * 60_000;

export function useMe() {
  return useQuery({
    queryKey: ["user", "me"],
    queryFn: () => unwrap(userApi.GET("/api/v1/me")),
    staleTime: FIVE_MINUTES,
  });
}

// id -> name of the user service's subject catalog (attendance records only carry subject ids).
export function useUserSubjectNames() {
  return useQuery({
    queryKey: ["user", "subjects"],
    queryFn: () =>
      unwrap(userApi.GET("/api/v1/subjects", { params: { query: { pageable: { size: 100 } } } })),
    staleTime: FIVE_MINUTES,
    select: (page) => new Map((page.content ?? []).map((s) => [s.id ?? "", s.name ?? ""])),
  });
}

// Profile of another user (teachers read their students); `enabled` keeps own-profile pages from asking.
export function useStudent(studentId: string, enabled = true) {
  return useQuery({
    queryKey: ["user", "student", studentId],
    enabled,
    queryFn: () =>
      unwrap(userApi.GET("/api/v1/students/{studentId}", { params: { path: { studentId } } })),
    staleTime: FIVE_MINUTES,
  });
}

// Teachers: the classrooms they teach.
export function useMyClassrooms(enabled = true) {
  return useQuery({
    queryKey: ["user", "my-classrooms"],
    enabled,
    queryFn: () => unwrap(userApi.GET("/api/v1/me/classrooms")),
    staleTime: FIVE_MINUTES,
  });
}

const classroomStudentsQuery = (classroomId: string) => ({
  queryKey: ["user", "classroom-students", classroomId],
  staleTime: FIVE_MINUTES,
  queryFn: () =>
    unwrap(
      userApi.GET("/api/v1/classrooms/{classroomId}/students", {
        params: { path: { classroomId } },
      }),
    ),
});

export function useClassroomStudents(classroomId: string | undefined) {
  return useQuery({ ...classroomStudentsQuery(classroomId ?? ""), enabled: !!classroomId });
}

// studentId -> "First Last" across the teacher's classrooms (assignments only carry student ids).
export function useMyStudentNames() {
  const classrooms = useMyClassrooms();
  const results = useQueries({
    queries: (classrooms.data ?? []).map((c) => classroomStudentsQuery(c.id ?? "")),
  });
  const names = new Map<string, string>();
  for (const result of results) {
    for (const s of result.data ?? []) {
      names.set(s.studentId ?? "", `${s.firstName ?? ""} ${s.lastName ?? ""}`.trim());
    }
  }
  return names;
}

// ---- Branch administration (role admin) ----
// User-service calls of the branch admin. Query keys start with "user" like the rest of the service's hooks.
type Schemas = components["schemas"];

const PAGE_SIZE = 100;
type PageOf<T> = { content?: T[]; page?: { totalPages?: number } };

// shortcut: loads every page one after the other (a branch has hundreds of rows, not thousands);
// move to server-side search once a list outgrows that.
async function fetchAll<T>(load: (page: number) => Promise<PageOf<T>>): Promise<T[]> {
  const first = await load(0);
  const rows = [...(first.content ?? [])];
  for (let page = 1; page < (first.page?.totalPages ?? 1); page++) {
    rows.push(...((await load(page)).content ?? []));
  }
  return rows;
}

export type UserRow =
  Schemas["StudentResponse"] | Schemas["TeacherResponse"] | Schemas["GuardianResponse"];
export type UserPage = { content?: UserRow[]; page?: Schemas["PageMetadata"] };

// Paged, filtered by active state and by `q` (name, or student number for students).
export function useUserPage(
  kind: UserKind,
  page: number,
  size: number,
  active: boolean | undefined,
  search = "",
) {
  return useQuery({
    queryKey: ["user", "list", kind, page, size, active, search],
    queryFn: (): Promise<UserPage> => {
      const query = { active, q: search.trim() || undefined, pageable: { page, size } };
      if (kind === "student") return unwrap(userApi.GET("/api/v1/students", { params: { query } }));
      if (kind === "teacher") return unwrap(userApi.GET("/api/v1/teachers", { params: { query } }));
      return unwrap(userApi.GET("/api/v1/guardians", { params: { query } }));
    },
    placeholderData: (previous) => previous,
  });
}

export function useTeacher(teacherId: string, enabled = true) {
  return useQuery({
    queryKey: ["user", "teacher", teacherId],
    enabled,
    queryFn: () =>
      unwrap(userApi.GET("/api/v1/teachers/{teacherId}", { params: { path: { teacherId } } })),
  });
}

export function useGuardian(guardianId: string, enabled = true) {
  return useQuery({
    queryKey: ["user", "guardian", guardianId],
    enabled,
    queryFn: () =>
      unwrap(userApi.GET("/api/v1/guardians/{guardianId}", { params: { path: { guardianId } } })),
  });
}

export function useGuardianChildren(guardianId: string) {
  return useQuery({
    queryKey: ["user", "guardian-children", guardianId],
    queryFn: () =>
      unwrap(
        userApi.GET("/api/v1/guardians/{guardianId}/children", {
          params: { path: { guardianId } },
        }),
      ),
  });
}

// Every student / teacher / classroom of the branch (pickers and name lookups).
export function useBranchStudents(enabled = true) {
  return useQuery({
    queryKey: ["user", "branch-students"],
    enabled,
    staleTime: FIVE_MINUTES,
    queryFn: () =>
      fetchAll((page) =>
        unwrap(
          userApi.GET("/api/v1/students", {
            params: { query: { pageable: { page, size: PAGE_SIZE } } },
          }),
        ),
      ),
  });
}

export function useBranchTeachers(enabled = true) {
  return useQuery({
    queryKey: ["user", "branch-teachers"],
    enabled,
    staleTime: FIVE_MINUTES,
    queryFn: () =>
      fetchAll((page) =>
        unwrap(
          userApi.GET("/api/v1/teachers", {
            params: { query: { pageable: { page, size: PAGE_SIZE } } },
          }),
        ),
      ),
  });
}

export function useBranchGuardians() {
  return useQuery({
    queryKey: ["user", "branch-guardians"],
    staleTime: FIVE_MINUTES,
    queryFn: () =>
      fetchAll((page) =>
        unwrap(
          userApi.GET("/api/v1/guardians", {
            params: { query: { pageable: { page, size: PAGE_SIZE } } },
          }),
        ),
      ),
  });
}

export function useBranchClassrooms(enabled = true) {
  return useQuery({
    queryKey: ["user", "branch-classrooms"],
    enabled,
    staleTime: FIVE_MINUTES,
    queryFn: () =>
      fetchAll((page) =>
        unwrap(
          userApi.GET("/api/v1/classrooms", {
            params: { query: { pageable: { page, size: PAGE_SIZE } } },
          }),
        ),
      ),
  });
}

export function useUserSubjects() {
  return useQuery({
    queryKey: ["user", "subject-list"],
    staleTime: FIVE_MINUTES,
    queryFn: () =>
      unwrap(userApi.GET("/api/v1/subjects", { params: { query: { pageable: { size: 100 } } } })),
    select: (page) => page.content ?? [],
  });
}

export function useClassroom(classroomId: string) {
  return useQuery({
    queryKey: ["user", "classroom", classroomId],
    queryFn: () =>
      unwrap(
        userApi.GET("/api/v1/classrooms/{classroomId}", { params: { path: { classroomId } } }),
      ),
  });
}

export function useClassroomTeachers(classroomId: string) {
  return useQuery({
    queryKey: ["user", "classroom-teachers", classroomId],
    queryFn: () =>
      unwrap(
        userApi.GET("/api/v1/classrooms/{classroomId}/teachers", {
          params: { path: { classroomId } },
        }),
      ),
  });
}

export function useTeacherAssignments(teacherId: string) {
  return useQuery({
    queryKey: ["user", "teacher-assignments", teacherId],
    queryFn: () =>
      unwrap(
        userApi.GET("/api/v1/teachers/{teacherId}/assignments", {
          params: { path: { teacherId } },
        }),
      ),
  });
}

// A student without a guardian answers 404; that is an ordinary state here, so it is not an error (no toast).
export function useStudentGuardian(studentId: string) {
  return useQuery({
    queryKey: ["user", "student-guardian", studentId],
    queryFn: async () => {
      try {
        return await unwrap(
          userApi.GET("/api/v1/students/{studentId}/guardian", {
            params: { path: { studentId } },
          }),
        );
      } catch (error) {
        if (error instanceof ApiError && error.problem.errorCode === "STUDENT_GUARDIAN_NOT_FOUND") {
          return null;
        }
        throw error;
      }
    },
  });
}

// Every mutation refreshes all user-service data; the lists are small and the screens few. A failed
// write may still have changed the record (CONCURRENT_MODIFICATION), so errors refresh too.
function useUserMutation<Variables, Result>(
  mutationFn: (variables: Variables) => Promise<Result>,
  successText: string | undefined,
  gcTime?: number,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    gcTime,
    onSuccess: async () => {
      if (successText) toast.success(successText);
      await queryClient.invalidateQueries({ queryKey: ["user"] });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: ["user"] }),
  });
}

export interface CreatedUser {
  id: string;
  passwordSetupEmailSent: boolean;
  temporaryPassword?: string;
  temporaryPasswordAlreadyIssued: boolean;
}

// The temporary password lives only in this result: gcTime 0 drops it with the component and the
// caller copies it into local state and calls reset(). It is never part of a query.
export function useCreateUser() {
  return useUserMutation(
    async ({
      call,
      idempotencyKey,
    }: {
      call: CreateCall;
      idempotencyKey: string;
    }): Promise<CreatedUser> => {
      const params = { header: { "Idempotency-Key": idempotencyKey } };
      const created =
        call.kind === "student"
          ? await unwrap(userApi.POST("/api/v1/students", { params, body: call.body }))
          : call.kind === "teacher"
            ? await unwrap(userApi.POST("/api/v1/teachers", { params, body: call.body }))
            : await unwrap(userApi.POST("/api/v1/guardians", { params, body: call.body }));
      return {
        id: created.user?.id ?? "",
        passwordSetupEmailSent: created.passwordSetupEmailSent ?? false,
        temporaryPassword: created.temporaryPassword,
        temporaryPasswordAlreadyIssued: created.temporaryPasswordAlreadyIssued ?? false,
      };
    },
    undefined,
    0,
  );
}

// The API takes no version: a concurrent edit is caught by the service, which answers CONCURRENT_MODIFICATION.
export function useUpdateUser(userId: string) {
  return useUserMutation((call: UpdateCall) => {
    if (call.kind === "student") {
      return unwrap(
        userApi.PATCH("/api/v1/students/{studentId}", {
          params: { path: { studentId: userId } },
          body: call.body,
        }),
      );
    }
    if (call.kind === "teacher") {
      return unwrap(
        userApi.PATCH("/api/v1/teachers/{teacherId}", {
          params: { path: { teacherId: userId } },
          body: call.body,
        }),
      );
    }
    return unwrap(
      userApi.PATCH("/api/v1/guardians/{guardianId}", {
        params: { path: { guardianId: userId } },
        body: call.body,
      }),
    );
  }, "Bilgiler güncellendi.");
}

export function useDeactivateUser(userId: string) {
  return useUserMutation(
    () =>
      unwrap(userApi.POST("/api/v1/users/{userId}/deactivate", { params: { path: { userId } } })),
    "Hesap pasife alındı.",
  );
}

export function useSendPasswordSetupEmail(userId: string) {
  return useUserMutation(async () => {
    const { response, error } = await userApi.POST("/api/v1/users/{userId}/password-setup-email", {
      params: { path: { userId } },
    });
    if (!response.ok) throw new ApiError(response.status, error);
  }, "Şifre belirleme e-postası gönderildi.");
}

// gcTime 0: the new password is returned once and must not linger in the mutation cache.
export function useResetTemporaryPassword(userId: string) {
  return useUserMutation(
    () =>
      unwrap(
        userApi.POST("/api/v1/users/{userId}/temporary-password", { params: { path: { userId } } }),
      ),
    undefined,
    0,
  );
}

export function useLinkGuardian(studentId: string) {
  return useUserMutation(
    (body: Schemas["LinkGuardianRequest"]) =>
      unwrap(
        userApi.PUT("/api/v1/students/{studentId}/guardian", {
          params: { path: { studentId } },
          body,
        }),
      ),
    "Veli bağlandı.",
  );
}

export function useUnlinkGuardian(studentId: string) {
  return useUserMutation(async () => {
    const { response, error } = await userApi.DELETE("/api/v1/students/{studentId}/guardian", {
      params: { path: { studentId } },
    });
    if (!response.ok) throw new ApiError(response.status, error);
  }, "Veli bağı kaldırıldı.");
}

export function useAssignClassroom(studentId: string) {
  return useUserMutation(
    (classroomId: string) =>
      unwrap(
        userApi.PUT("/api/v1/students/{studentId}/classroom", {
          params: { path: { studentId } },
          body: { classroomId },
        }),
      ),
    "Öğrenci sınıfa eklendi.",
  );
}

export function useRemoveFromClassroom() {
  return useUserMutation(
    (studentId: string) =>
      unwrap(
        userApi.DELETE("/api/v1/students/{studentId}/classroom", {
          params: { path: { studentId } },
        }),
      ),
    "Öğrenci sınıftan çıkarıldı.",
  );
}

export function useCreateClassroom() {
  return useUserMutation(
    ({
      body,
      idempotencyKey,
    }: {
      body: Schemas["CreateClassroomRequest"];
      idempotencyKey: string;
    }) =>
      unwrap(
        userApi.POST("/api/v1/classrooms", {
          params: { header: { "Idempotency-Key": idempotencyKey } },
          body,
        }),
      ),
    "Sınıf oluşturuldu.",
  );
}

export function useUpdateClassroom(classroomId: string) {
  return useUserMutation(
    (body: Schemas["UpdateClassroomRequest"]) =>
      unwrap(
        userApi.PATCH("/api/v1/classrooms/{classroomId}", {
          params: { path: { classroomId } },
          body,
        }),
      ),
    "Sınıf güncellendi.",
  );
}

export function useDeactivateClassroom(classroomId: string) {
  return useUserMutation(
    () =>
      unwrap(
        userApi.POST("/api/v1/classrooms/{classroomId}/deactivate", {
          params: { path: { classroomId } },
        }),
      ),
    "Sınıf pasife alındı.",
  );
}

export function useCreateTeacherAssignment(teacherId: string) {
  return useUserMutation(
    ({
      body,
      idempotencyKey,
    }: {
      body: Schemas["CreateTeacherAssignmentRequest"];
      idempotencyKey: string;
    }) =>
      unwrap(
        userApi.POST("/api/v1/teachers/{teacherId}/assignments", {
          params: { path: { teacherId }, header: { "Idempotency-Key": idempotencyKey } },
          body,
        }),
      ),
    "Öğretmen atandı.",
  );
}

export function useDeleteTeacherAssignment(teacherId: string) {
  return useUserMutation(async (assignmentId: string) => {
    const { response, error } = await userApi.DELETE(
      "/api/v1/teachers/{teacherId}/assignments/{assignmentId}",
      { params: { path: { teacherId, assignmentId } } },
    );
    if (!response.ok) throw new ApiError(response.status, error);
  }, "Atama kaldırıldı.");
}
