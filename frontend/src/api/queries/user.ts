import { useQueries, useQuery } from "@tanstack/react-query";
import { unwrap, userApi } from "../client";

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
export function useMyClassrooms() {
  return useQuery({
    queryKey: ["user", "my-classrooms"],
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
