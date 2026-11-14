import type { ReactNode } from "react";
import { Navigate } from "react-router";
import type { Role } from "./actor";
import { useActor } from "./useActor";

export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const actor = useActor();
  if (!actor?.roles.some((role) => roles.includes(role))) return <Navigate to="/" replace />;
  return children;
}
