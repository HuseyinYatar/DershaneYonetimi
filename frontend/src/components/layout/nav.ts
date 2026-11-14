import {
  Bell,
  CalendarCheck,
  FileText,
  LayoutDashboard,
  ListChecks,
  School,
  MessageCircle,
  type LucideIcon,
} from "lucide-react";
import type { Role } from "@/auth/actor";

export interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
  // Shown in the phone tab bar; the rest go under "Daha fazla".
  tab: boolean;
  // Carries the unread-notification count.
  badge?: boolean;
  // Active on sub-routes too (results/:id under Denemeler), not only on the exact path.
  prefix?: boolean;
}

// Menu only; the services decide what a role may actually do. Guardians and admins get no items yet.
export function navForRoles(roles: Role[], userId: string): NavItem[] {
  if (roles.includes("teacher") && !roles.includes("student")) {
    return [
      { label: "Özet", to: "/", icon: LayoutDashboard, tab: true },
      { label: "Yoklama", to: "/sessions", icon: CalendarCheck, tab: true, prefix: true },
      { label: "Görevler", to: "/given-tasks", icon: ListChecks, tab: true, prefix: true },
      { label: "Sınıflarım", to: "/classrooms", icon: School, tab: true, prefix: true },
      { label: "Denemeler", to: "/exams", icon: FileText, tab: false, prefix: true },
      { label: "Mesajlar", to: "/messages", icon: MessageCircle, tab: false },
    ];
  }
  if (!roles.includes("student")) return [];
  return [
    { label: "Özet", to: `/students/${userId}`, icon: LayoutDashboard, tab: true },
    {
      label: "Denemeler",
      to: `/students/${userId}/results`,
      icon: FileText,
      tab: true,
      prefix: true,
    },
    { label: "Görevlerim", to: "/tasks", icon: ListChecks, tab: true, prefix: true },
    { label: "Yoklama", to: "/attendance", icon: CalendarCheck, tab: true },
    { label: "Mesajlar", to: "/messages", icon: MessageCircle, tab: false },
    { label: "Bildirimler", to: "/notifications", icon: Bell, tab: false, badge: true },
  ];
}
