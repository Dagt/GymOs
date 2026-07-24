"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { getCurrentUser, getStoredSession, storeCurrentUser, type CurrentUser } from "@/lib/api";
import { isPlatformAdmin, isSupportMode } from "@/lib/role-model";

type ThemeConfig = {
  name: string;
  logoUrl: string;
  primary: string;
  accent: string;
  surface: string;
};

const defaultTheme: ThemeConfig = {
  name: "GymOS",
  logoUrl: "",
  primary: "#164E63",
  accent: "#F59E0B",
  surface: "#F4F7FB",
};

export function TenantThemeProvider() {
  const pathname = usePathname();

  useEffect(() => {
    let cancelled = false;
    const session = getStoredSession();
    const isPlatformRoute = pathname.startsWith("/platform");

    if (!session || isPlatformRoute || (isPlatformAdmin(session.user) && !isSupportMode(session.user))) {
      applyTheme(defaultTheme);
      return;
    }

    applyTheme(themeFromUser(session.user));

    async function refreshMissingTheme() {
      if (!session || hasCompleteTheme(session.user)) return;
      try {
        const currentUser = await getCurrentUser(session.accessToken);
        storeCurrentUser(currentUser);
        if (!cancelled) applyTheme(themeFromUser(currentUser));
      } catch {
        // The embedded session theme remains active when profile refresh is unavailable.
      }
    }

    void refreshMissingTheme();
    return () => {
      cancelled = true;
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
  root.style.setProperty("--brand-name", `"${theme.name || "GymOS"}"`);
  root.style.setProperty("--brand-logo-url", theme.logoUrl ? `url("${theme.logoUrl}")` : "none");
  root.style.setProperty("--brand-primary", theme.primary || defaultTheme.primary);
  root.style.setProperty("--brand-accent", theme.accent || defaultTheme.accent);
  root.style.setProperty("--brand-surface", theme.surface || defaultTheme.surface);
}
