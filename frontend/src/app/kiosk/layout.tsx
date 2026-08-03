"use client";

import { useEffect, type ReactNode } from "react";
import { clearKioskLaunch } from "@/lib/kiosk-launch";

export default function KioskLayout({ children }: { children: ReactNode }) {
  useEffect(() => {
    const closeRestoredPage = (event: PageTransitionEvent) => {
      if (!event.persisted) return;
      clearKioskLaunch();
      window.location.replace("/kiosk/closed?reason=restored");
    };
    const blockBackNavigation = () => {
      clearKioskLaunch();
      window.location.replace("/kiosk/closed?reason=navigation");
    };

    window.addEventListener("pageshow", closeRestoredPage);
    window.addEventListener("popstate", blockBackNavigation);
    return () => {
      window.removeEventListener("pageshow", closeRestoredPage);
      window.removeEventListener("popstate", blockBackNavigation);
    };
  }, []);

  return children;
}
