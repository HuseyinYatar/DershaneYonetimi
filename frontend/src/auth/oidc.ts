import { UserManager } from "oidc-client-ts";
import { getConfig } from "@/config";

let manager: UserManager | undefined;

// Shared by AuthProvider and the API client (which lives outside React). Tokens stay in the
// default sessionStorage: cleared when the tab closes, which is better for KVKK than localStorage.
export function getUserManager(): UserManager {
  manager ??= new UserManager({
    authority: `${getConfig().keycloakUrl}/realms/dershane`,
    client_id: "dershane-frontend",
    redirect_uri: `${window.location.origin}/`,
    post_logout_redirect_uri: `${window.location.origin}/`,
    automaticSilentRenew: true,
  });
  return manager;
}
