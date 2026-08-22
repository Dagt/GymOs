"use client";

import type { ReactNode } from "react";

export function AppDrawer({
  children,
  description,
  onClose,
  open,
  title,
  wide = false,
}: {
  children: ReactNode;
  description?: string;
  onClose: () => void;
  open: boolean;
  title: string;
  wide?: boolean;
}) {
  if (!open) {
    return null;
  }

  return (
    <aside className="app-drawer" aria-modal="true" role="dialog">
      <button className="app-drawer-backdrop" onClick={onClose} type="button" aria-label="Cerrar panel" />
      <section
        className={`app-drawer-card${wide ? " wide" : ""} max-h-[calc(100dvh-1.5rem)] grid-rows-[auto_minmax(0,1fr)] overflow-hidden`}
      >
        <header>
          <div>
            <p>Panel de trabajo</p>
            <h2>{title}</h2>
            {description ? <span>{description}</span> : null}
          </div>
          <button onClick={onClose} type="button">
            Cerrar
          </button>
        </header>
        <div className="app-drawer-body min-h-0 overflow-y-auto overscroll-contain pr-1 [&_.max-h-64]:!static [&_.max-h-64]:!max-h-none [&_.max-h-64]:!overflow-visible">
          {children}
        </div>
      </section>
    </aside>
  );
}
