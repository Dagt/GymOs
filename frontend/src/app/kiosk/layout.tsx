"use client";

import { useEffect, type ReactNode } from "react";

function clearEphemeralBrowserState() {
  window.sessionStorage.clear();
  window.history.replaceState(null, "", window.location.pathname);
}

export default function KioskLayout({ children }: { children: ReactNode }) {
  useEffect(() => {
    const closeRestoredPage = (event: PageTransitionEvent) => {
      if (!event.persisted) return;
      clearEphemeralBrowserState();
      window.location.replace("/kiosk/closed?reason=restored");
    };
    const blockBackNavigation = () => {
      clearEphemeralBrowserState();
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
