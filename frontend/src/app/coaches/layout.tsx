import type { ReactNode } from "react";
import { CoachesExperienceEnhancer } from "./CoachesExperienceEnhancer";

export default function CoachesLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <CoachesExperienceEnhancer />
      {children}
    </>
  );
}
