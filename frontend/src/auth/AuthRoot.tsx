import type { ReactNode } from "react";
import { AuthProvider } from "react-oidc-context";
import { useNavigate } from "react-router";
import { getUserManager } from "./oidc";
import { returnPath } from "./returnPath";

// Must sit inside the router: after the callback it navigates (replace) to the page saved in
// `state`, which also drops ?code=&state= from the URL.
export function AuthRoot({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  return (
    <AuthProvider
      userManager={getUserManager()}
      onSigninCallback={(user) => navigate(returnPath(user?.state), { replace: true })}
    >
      {children}
    </AuthProvider>
  );
}
