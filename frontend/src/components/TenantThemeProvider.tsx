"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { getCurrentOrganization, getStoredSession, type OrganizationSummary } from "@/lib/api";
import { isPlatformAdmin } from "@/lib/role-model";

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

    if (!session || isPlatformRoute || isPlatformAdmin(session.user)) {
      applyTheme(defaultTheme);
      return;
    }

    async function loadTenantTheme() {
      if (!session) {
        return;
      }
      try {
        const organization = await getCurrentOrganization(session.accessToken);
        if (!cancelled) {
          applyTheme(themeFromOrganization(organization));
        }
      } catch {
        if (!cancelled) {
          applyTheme(defaultTheme);
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

function applyTheme(theme: ThemeConfig) {
  const root = document.documentElement;
  root.style.setProperty("--brand-name", `"${theme.name || "GymOS"}"`);
  root.style.setProperty("--brand-logo-url", theme.logoUrl ? `url("${theme.logoUrl}")` : "none");
  root.style.setProperty("--brand-primary", theme.primary || defaultTheme.primary);
  root.style.setProperty("--brand-accent", theme.accent || defaultTheme.accent);
  root.style.setProperty("--brand-surface", theme.surface || defaultTheme.surface);
}
