"use client";

import type { ReactNode } from "react";

export function AppDrawer({
  children,
  description,
  onClose,
  open,
  title,
}: {
  children: ReactNode;
  description?: string;
  onClose: () => void;
  open: boolean;
  title: string;
}) {
  if (!open) {
    return null;
  }

  return (
    <aside className="app-drawer" aria-modal="true" role="dialog">
      <button className="app-drawer-backdrop" onClick={onClose} type="button" aria-label="Cerrar panel" />
      <section className="app-drawer-card">
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
        <div className="app-drawer-body">{children}</div>
      </section>
    </aside>
  );
}
