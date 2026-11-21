import { useEffect, type ReactNode } from "react";
import { TriangleAlert, Unplug } from "lucide-react";
import { useAuth } from "react-oidc-context";
import { isNetworkError, isOffline } from "@/api/problem";
import { currentPath } from "./returnPath";

export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const needsLogin =
    !auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator && !auth.error;

  useEffect(() => {
    if (needsLogin) void auth.signinRedirect({ state: currentPath() });
  }, [needsLogin, auth]);

  if (auth.error) {
    const unreachable = isNetworkError(auth.error);
    const offline = unreachable && isOffline();
    return (
      <main className="grid min-h-screen place-items-center bg-canvas p-4">
        <section className="flex w-full max-w-sm flex-col rounded-[20px] bg-brand p-6 text-white">
          <div className="flex items-center justify-between">
            <span className="inline-flex h-8 items-center rounded-full bg-white/[0.18] px-3 text-[13px]">
              {unreachable ? "Bağlantı yok" : "Giriş hatası"}
            </span>
            <span className="grid size-10 place-items-center rounded-full bg-white/[0.18]">
              {unreachable ? (
                <Unplug size={20} strokeWidth={1.5} aria-hidden="true" />
              ) : (
                <TriangleAlert size={20} strokeWidth={1.5} aria-hidden="true" />
              )}
            </span>
          </div>
          <h1 className="mt-10 text-3xl leading-[1.15] font-normal tracking-[-0.02em]">
            {!unreachable
              ? "Giriş yapılamadı"
              : offline
                ? "İnternet bağlantınız yok"
                : "Şu an bağlanamıyoruz"}
          </h1>
          <p className="mt-3 text-[13px] text-white/85">
            {!unreachable
              ? "Giriş sırasında bir sorun oluştu. Tekrar deneyin."
              : offline
                ? "Bağlantınızı kontrol edip tekrar deneyin."
                : "İnternet bağlantınızı kontrol edin. Sorun sürerse biraz sonra tekrar deneyin."}
          </p>
          <button
            type="button"
            className="mt-8 h-11 rounded-full bg-white px-5 text-sm font-medium text-ink"
            onClick={() => void auth.signinRedirect({ state: currentPath() })}
          >
            Tekrar dene
          </button>
        </section>
      </main>
    );
  }
  if (!auth.isAuthenticated) return <div className="boot" role="status" aria-label="Yükleniyor" />; // same spinner as index.html, so React taking over does not flicker
  return children;
}
