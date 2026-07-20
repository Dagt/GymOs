import type { ReactNode } from "react";
import KioskBirthDateField from "./KioskBirthDateField";

export default function KioskTerminalLayout({ children }: { children: ReactNode }) {
  return (
    <>
      {children}
      <KioskBirthDateField />
    </>
  );
}
