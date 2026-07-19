"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { getCurrentOrganization, getCurrentUser, getStoredSession, storeCurrentUser, type CurrentUser, type OrganizationSummary } from "@/lib/api";
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

    async function loadTenantTheme() {
      if (!session) {
        return;
      }
      let themedUser = session.user;
      try {
        if (!session.user.primaryColor && !session.user.logoUrl && !session.user.brandName) {
          const currentUser = await getCurrentUser(session.accessToken);
          themedUser = currentUser;
          storeCurrentUser(currentUser);
          if (!cancelled) {
            applyTheme(themeFromUser(currentUser));
          }
        }
        const organization = await getCurrentOrganization(session.accessToken);
        if (!cancelled) {
          applyTheme(themeFromOrganization(organization));
        }
      } catch {
        if (!cancelled) {
          applyTheme(themeFromUser(themedUser));
        }
      }
    }

    void loadTenantTheme();

    return () => {
      cancelled = true;
    };
  }, [pathname]);

  return null;
}

export function themeFromOrganization(organization: OrganizationSummary): ThemeConfig {
  return {
    name: organization.brandName || organization.tradeName || defaultTheme.name,
    logoUrl: organization.logoUrl || "",
    primary: organization.primaryColor || defaultTheme.primary,
    accent: organization.accentColor || defaultTheme.accent,
    surface: organization.surfaceColor || defaultTheme.surface,
  };
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
