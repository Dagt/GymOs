"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { getStoredSession } from "@/lib/api";
import { canAccessRoute, getPostLoginPath } from "@/lib/role-model";

export function GlobalSessionNav() {
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    const session = getStoredSession();
    if (!session) {
      return;
    }
    if (!canAccessRoute(session.user, pathname)) {
      router.replace(getPostLoginPath(session.user));
    }
  }, [pathname, router]);

  return null;
}
