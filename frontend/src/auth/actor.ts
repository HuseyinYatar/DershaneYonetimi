export const ROLES = ["student", "teacher", "guardian", "admin", "super-admin"] as const;
export type Role = (typeof ROLES)[number];

export interface Actor {
  id: string;
  roles: Role[];
  branchId?: string;
}

// Only used for menus and redirects. Authorization is always enforced by the services.
export function decodeActor(accessToken: string | undefined): Actor | null {
  if (!accessToken) return null;
  try {
    const base64 = accessToken.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="));
    const claims = JSON.parse(
      new TextDecoder().decode(Uint8Array.from(binary, (c) => c.charCodeAt(0))),
    );
    if (typeof claims.sub !== "string") return null;
    const tokenRoles: unknown[] = Array.isArray(claims.realm_access?.roles)
      ? claims.realm_access.roles
      : [];
    return {
      id: claims.sub,
      roles: ROLES.filter((role) => tokenRoles.includes(role)),
      branchId: typeof claims.branch_id === "string" ? claims.branch_id : undefined,
    };
  } catch {
    return null;
  }
}
