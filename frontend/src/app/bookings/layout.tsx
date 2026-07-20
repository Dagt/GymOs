import type { ReactNode } from "react";
import BookingsNotesEnhancer from "./BookingsNotesEnhancer";
import styles from "./bookings-layout.module.css";

export default function BookingsLayout({ children }: { children: ReactNode }) {
  return (
    <div className={styles.shell}>
      {children}
      <BookingsNotesEnhancer />
    </div>
  );
}
