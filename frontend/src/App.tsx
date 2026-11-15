import type { ReactNode } from "react";
import { Navigate, Outlet, Route, Routes } from "react-router";
import { RequireAuth } from "@/auth/RequireAuth";
import { RequireRole } from "@/auth/RequireRole";
import { useActor } from "@/auth/useActor";
import { Placeholder } from "@/components/Placeholder";
import { AppShell } from "@/components/layout/AppShell";
import { AttendancePage } from "@/features/attendance/AttendancePage";
import { AdminClassroomDetailPage } from "@/features/classrooms/AdminClassroomDetailPage";
import { AdminClassroomsPage } from "@/features/classrooms/AdminClassroomsPage";
import { ClassroomDetailPage } from "@/features/classrooms/ClassroomDetailPage";
import { ClassroomsPage } from "@/features/classrooms/ClassroomsPage";
import { DashboardPage } from "@/features/dashboard/DashboardPage";
import { ExcusesPage } from "@/features/excuses/ExcusesPage";
import { ExamDetailPage } from "@/features/exams/ExamDetailPage";
import { ExamsPage } from "@/features/exams/ExamsPage";
import { GivenTaskDetailPage } from "@/features/given-tasks/GivenTaskDetailPage";
import { GivenTasksPage } from "@/features/given-tasks/GivenTasksPage";
import { NewTaskPage } from "@/features/given-tasks/NewTaskPage";
import { NotificationsPage } from "@/features/notifications/NotificationsPage";
import { ResultDetailPage } from "@/features/results/ResultDetailPage";
import { ResultsPage } from "@/features/results/ResultsPage";
import { SchedulesPage } from "@/features/schedules/SchedulesPage";
import { SessionDetailPage } from "@/features/sessions/SessionDetailPage";
import { SessionsPage } from "@/features/sessions/SessionsPage";
import { TeacherOverviewPage } from "@/features/teacher-overview/TeacherOverviewPage";
import { TaskDetailPage } from "@/features/tasks/TaskDetailPage";
import { TasksPage } from "@/features/tasks/TasksPage";
import { UserCreatePage } from "@/features/users/UserCreatePage";
import { UserDetailPage } from "@/features/users/UserDetailPage";
import { UsersPage } from "@/features/users/UsersPage";
import { WeakTopicsPage } from "@/features/weak-topics/WeakTopicsPage";

// Admins manage the branch's classrooms; teachers see only theirs.
function ByRole({ admin, other }: { admin: ReactNode; other: ReactNode }) {
  return useActor()?.roles.includes("admin") ? admin : other;
}

function Home() {
  const actor = useActor();
  if (actor?.roles.includes("admin")) return <Navigate to="/users" replace />;
  if (actor?.roles.includes("student")) return <Navigate to={`/students/${actor.id}`} replace />;
  if (actor?.roles.includes("teacher")) return <TeacherOverviewPage />;
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
              <RequireRole roles={["student", "teacher", "admin"]}>
                <Outlet />
              </RequireRole>
            }
          >
            <Route path="students/:studentId" element={<DashboardPage />} />
            <Route path="students/:studentId/results" element={<ResultsPage />} />
            <Route path="students/:studentId/results/:mockExamId" element={<ResultDetailPage />} />
            <Route path="students/:studentId/weak-topics" element={<WeakTopicsPage />} />
            <Route path="messages" element={<Placeholder title="Mesajlar" />} />
            <Route path="settings" element={<Placeholder title="Ayarlar" />} />
          </Route>
          <Route
            element={
              <RequireRole roles={["student"]}>
                <Outlet />
              </RequireRole>
            }
          >
            <Route path="tasks" element={<TasksPage />} />
            <Route path="tasks/:assignmentId" element={<TaskDetailPage />} />
            <Route path="attendance" element={<AttendancePage />} />
            <Route path="notifications" element={<NotificationsPage />} />
          </Route>
          <Route
            element={
              <RequireRole roles={["teacher"]}>
                <Outlet />
              </RequireRole>
            }
          >
            <Route path="overview" element={<TeacherOverviewPage />} />
            <Route path="given-tasks" element={<GivenTasksPage />} />
            <Route path="given-tasks/new" element={<NewTaskPage />} />
            <Route path="given-tasks/:taskId" element={<GivenTaskDetailPage />} />
          </Route>
          <Route
            element={
              <RequireRole roles={["teacher", "admin"]}>
                <Outlet />
              </RequireRole>
            }
          >
            <Route path="sessions" element={<SessionsPage />} />
            <Route path="sessions/:sessionId" element={<SessionDetailPage />} />
            <Route
              path="classrooms"
              element={<ByRole admin={<AdminClassroomsPage />} other={<ClassroomsPage />} />}
            />
            <Route
              path="classrooms/:classroomId"
              element={
                <ByRole admin={<AdminClassroomDetailPage />} other={<ClassroomDetailPage />} />
              }
            />
            <Route path="exams" element={<ExamsPage />} />
            <Route path="exams/:mockExamId" element={<ExamDetailPage />} />
          </Route>
          <Route
            element={
              <RequireRole roles={["admin"]}>
                <Outlet />
              </RequireRole>
            }
          >
            <Route path="users" element={<UsersPage />} />
            <Route path="users/new" element={<UserCreatePage />} />
            <Route path="users/students/:userId" element={<UserDetailPage kind="student" />} />
            <Route path="users/teachers/:userId" element={<UserDetailPage kind="teacher" />} />
            <Route path="users/guardians/:userId" element={<UserDetailPage kind="guardian" />} />
            <Route path="schedules" element={<SchedulesPage />} />
            <Route path="excuses" element={<ExcusesPage />} />
          </Route>
          <Route path="*" element={<p>Sayfa bulunamadı.</p>} />
        </Route>
      </Routes>
    </RequireAuth>
  );
}
