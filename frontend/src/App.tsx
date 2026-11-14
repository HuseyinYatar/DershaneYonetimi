import { Navigate, Outlet, Route, Routes } from "react-router";
import { RequireAuth } from "@/auth/RequireAuth";
import { RequireRole } from "@/auth/RequireRole";
import { useActor } from "@/auth/useActor";
import { Placeholder } from "@/components/Placeholder";
import { AppShell } from "@/components/layout/AppShell";
import { AttendancePage } from "@/features/attendance/AttendancePage";
import { DashboardPage } from "@/features/dashboard/DashboardPage";
import { NotificationsPage } from "@/features/notifications/NotificationsPage";
import { ResultDetailPage } from "@/features/results/ResultDetailPage";
import { ResultsPage } from "@/features/results/ResultsPage";
import { TaskDetailPage } from "@/features/tasks/TaskDetailPage";
import { TasksPage } from "@/features/tasks/TasksPage";
import { WeakTopicsPage } from "@/features/weak-topics/WeakTopicsPage";

function Home() {
  const actor = useActor();
  if (actor?.roles.includes("student")) return <Navigate to={`/students/${actor.id}`} replace />;
  return <Placeholder title="Yakında" />;
}

export function App() {
  return (
    <RequireAuth>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<Home />} />
          <Route
            element={
              <RequireRole roles={["student"]}>
                <Outlet />
              </RequireRole>
            }
          >
            <Route path="students/:studentId" element={<DashboardPage />} />
            <Route path="students/:studentId/results" element={<ResultsPage />} />
            <Route path="students/:studentId/results/:mockExamId" element={<ResultDetailPage />} />
            <Route path="students/:studentId/weak-topics" element={<WeakTopicsPage />} />
            <Route path="tasks" element={<TasksPage />} />
            <Route path="tasks/:assignmentId" element={<TaskDetailPage />} />
            <Route path="attendance" element={<AttendancePage />} />
            <Route path="notifications" element={<NotificationsPage />} />
            <Route path="messages" element={<Placeholder title="Mesajlar" />} />
            <Route path="settings" element={<Placeholder title="Ayarlar" />} />
          </Route>
          <Route path="*" element={<p>Sayfa bulunamadı.</p>} />
        </Route>
      </Routes>
    </RequireAuth>
  );
}
