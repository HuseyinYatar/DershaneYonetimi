import { useQuery } from "@tanstack/react-query";
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
