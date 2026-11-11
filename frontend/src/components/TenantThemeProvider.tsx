"use client";

import { usePathname } from "next/navigation";
import { useLayoutEffect, useRef } from "react";
import { getCurrentUser, getStoredSession, storeCurrentUser, type CurrentUser, type StoredSession } from "@/lib/api";
import { sameAccessContext, sameUserContext } from "@/lib/api/session";
import { isPlatformAdmin, isSupportMode } from "@/lib/role-model";

type ThemeConfig = {
  name: string;
  logoUrl: string;
  primary: string;
  accent: string;
  surface: string;
};

const defaultTheme: ThemeConfig = {
  name: "GymFort",
  logoUrl: "",
  primary: "#093D3A",
  accent: "#18C8B2",
  surface: "#F2F8F7",
};

export function TenantThemeProvider() {
  const pathname = usePathname();

  const attemptedProfiles = useRef(new WeakSet<CurrentUser>());
  const mounted = useRef(false);
  const pendingProfiles = useRef(new Set<string>());
  const synchronizeLatest = useRef<() => void>(() => {});
  const invalidatedSession = useRef<StoredSession | null>(null);

  useLayoutEffect(() => {
    mounted.current = true;
    function synchronize() {
      const session = getStoredSession();
      if (!session || (invalidatedSession.current && sameAccessContext(session.accessToken, invalidatedSession.current.accessToken)) || pathname.startsWith("/platform") || (isPlatformAdmin(session.user) && !isSupportMode(session.user))) {
        applyTheme(defaultTheme);
        return;
      }
      applyTheme(themeFromUser(session.user));
      const key = JSON.stringify([session.user.userId, session.user.accountScope, session.user.organizationId, session.user.branchId, session.user.supportMode]);
      if (hasCompleteTheme(session.user) || attemptedProfiles.current.has(session.user) || pendingProfiles.current.has(key)) return;
      pendingProfiles.current.add(key);
      attemptedProfiles.current.add(session.user);
      let succeeded = false;
      void getCurrentUser(session.accessToken).then((user) => {
        succeeded = true;
        if (!mounted.current || (invalidatedSession.current && sameAccessContext(session.accessToken, invalidatedSession.current.accessToken))) return;
        // Mark even an incomplete response before notifying, so it cannot trigger a fetch loop.
        attemptedProfiles.current.add(user);
        storeCurrentUser(user, session);
      }).catch(() => {
        // A 401 can renew the token while this request is in flight. Do not start a retry loop.
        const current = getStoredSession();
        if (current && sameUserContext(current.user, session.user)) attemptedProfiles.current.add(current.user);
      }).finally(() => {
        pendingProfiles.current.delete(key);
        // A newer context/profile may have arrived during this request. Re-read it, never apply its predecessor.
        if (succeeded && mounted.current) synchronizeLatest.current();
      });
    }
    function invalidate() {
      invalidatedSession.current = getStoredSession();
      applyTheme(defaultTheme);
    }
    synchronizeLatest.current = synchronize;
    window.addEventListener("gymos.session-refreshed", synchronize);
    window.addEventListener("gymfort.session-context-changed", invalidate);
    synchronize();
    return () => {
      mounted.current = false;
      window.removeEventListener("gymos.session-refreshed", synchronize);
      window.removeEventListener("gymfort.session-context-changed", invalidate);
    };
  }, [pathname]);

  return null;
}

function hasCompleteTheme(user: CurrentUser) {
  return Boolean(user.primaryColor && user.accentColor && user.surfaceColor);
}

function themeFromUser(user: CurrentUser): ThemeConfig {
  return {
    name: user.brandName || user.organizationName || defaultTheme.name,
    logoUrl: user.logoUrl || "",
    primary: user.primaryColor || defaultTheme.primary,
    accent: user.accentColor || defaultTheme.accent,
    surface: user.surfaceColor || defaultTheme.surface,
  };
}

function applyTheme(theme: ThemeConfig) {
  const root = document.documentElement;
  const properties = {
    "--brand-name": `"${theme.name || "GymFort"}"`,
    "--brand-logo-url": theme.logoUrl ? `url("${theme.logoUrl}")` : "none",
    "--brand-primary": theme.primary || defaultTheme.primary,
    "--brand-accent": theme.accent || defaultTheme.accent,
    "--brand-surface": theme.surface || defaultTheme.surface,
  };
  for (const [property, value] of Object.entries(properties)) {
    if (root.style.getPropertyValue(property) !== value) root.style.setProperty(property, value);
  }
}
