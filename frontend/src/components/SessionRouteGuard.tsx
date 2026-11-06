"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { getStoredSession, restoreSession } from "@/lib/api";
import { canAccessRoute, getPostLoginPath } from "@/lib/role-model";
import { AuthRequired } from "./AuthRequired";

const PUBLIC_PAGES = new Set([
  "/", "/login", "/support", "/accept-invitation", "/reset-password",
  "/privacidad", "/privacy-policy", "/terminos", "/terms-of-service",
]);

export function SessionRouteGuard({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const publicPage = PUBLIC_PAGES.has(pathname) || (pathname.startsWith("/kiosk/") && pathname !== "/kiosk/launch");
  const [status, setStatus] = useState<"loading" | "ready" | "expired" | "unavailable" | "changed">("loading");

  useEffect(() => {
    if (publicPage) return;
    let active = true;
    const expired = () => setStatus("expired");
    const changed = () => setStatus("changed");
    window.addEventListener("gymfort.session-expired", expired);
    window.addEventListener("gymfort.session-context-changed", changed);
    void restoreSession().then((result) => {
      if (!active) return;
      const session = getStoredSession();
      if (result === "refreshed" && session) {
        if (!canAccessRoute(session.user, pathname)) {
          router.replace(getPostLoginPath(session.user));
        }
        setStatus("ready");
      } else {
        setStatus(result === "unavailable" || result === "changed" ? result : "expired");
      }
    });
    return () => {
      active = false;
      window.removeEventListener("gymfort.session-expired", expired);
      window.removeEventListener("gymfort.session-context-changed", changed);
    };
  }, [pathname, publicPage, router]);

  if (publicPage) return children;
  if (status === "ready" && getStoredSession()) {
    const user = getStoredSession()!.user;
    if (canAccessRoute(user, pathname)) return children;
  }
  if (status === "expired") return <AuthRequired message="Tu sesión terminó o todavía no has iniciado sesión en esta pestaña." />;
  return (
    <main className="auth-required-screen">
      <section className="auth-required-card" role="status">
        <h1>{status === "unavailable" ? "No se pudo restaurar la sesión"
          : status === "changed" ? "La sesión cambió en otra pestaña" : "Comprobando tu sesión…"}</h1>
        {status === "unavailable" || status === "changed" ? (
          <button className="app-button" onClick={() => window.location.reload()} type="button">
            {status === "changed" ? "Recargar panel" : "Reintentar"}
          </button>
        ) : null}
      </section>
    </main>
  );
}
