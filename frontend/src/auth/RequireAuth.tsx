import { useEffect, type ReactNode } from "react";
import { useAuth } from "react-oidc-context";
import { currentPath } from "./returnPath";

export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const needsLogin =
    !auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator && !auth.error;

  useEffect(() => {
    if (needsLogin) void auth.signinRedirect({ state: currentPath() });
  }, [needsLogin, auth]);

  if (auth.error) {
    return (
      <div className="p-6">
        <p>Giriş yapılamadı.</p>
        <button
          className="mt-2 underline"
          onClick={() => void auth.signinRedirect({ state: currentPath() })}
        >
          Tekrar dene
        </button>
      </div>
    );
  }
  if (!auth.isAuthenticated) return <div className="boot" role="status" aria-label="Yükleniyor" />; // same spinner as index.html, so React taking over does not flicker
  return children;
}
