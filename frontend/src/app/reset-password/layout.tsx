import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = {
  title: "Recuperar contraseña",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function PasswordRecoveryLayout({ children }: { children: ReactNode }) { return children; }
