import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { notificationApi, unwrap } from "../client";

export function useNotifications() {
  return useQuery({
    queryKey: ["notification", "list"],
    queryFn: () =>
      unwrap(notificationApi.GET("/api/v1/notifications", { params: { query: { limit: 50 } } })),
  });
}

export function useUnreadCount() {
  return useQuery({
    queryKey: ["notification", "unread-count"],
    queryFn: () => unwrap(notificationApi.GET("/api/v1/notifications/unread-count")),
    refetchInterval: 60_000,
    select: (data) => data.count,
  });
}

export function useMarkRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        notificationApi.POST("/api/v1/notifications/{notification_id}/read", {
          params: { path: { notification_id: id } },
        }),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notification"] }),
  });
}

export function useMarkAllRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => unwrap(notificationApi.POST("/api/v1/notifications/read-all")),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notification"] }),
  });
}
