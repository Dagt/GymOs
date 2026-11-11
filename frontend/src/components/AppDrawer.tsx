"use client";

import { useEffect, useRef, type ReactNode } from "react";

export function AppDrawer({
  children,
  description,
  onClose,
  open,
  title,
  wide = false,
  containFocus = false,
}: {
  children: ReactNode;
  description?: string;
  onClose: () => void;
  open: boolean;
  title: string;
  wide?: boolean;
  containFocus?: boolean;
}) {
  const panel = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!open || !containFocus) return;
    const previous = document.activeElement as HTMLElement | null;
    const focusable = () => Array.from(panel.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]') ?? []);
    focusable()[0]?.focus();
    return () => { previous?.focus(); };
  }, [open, containFocus]);

  if (!open) {
    return null;
  }

  return (
    <aside className="app-drawer" aria-modal="true" role="dialog">
      <button className="app-drawer-backdrop" onClick={onClose} type="button" aria-label="Cerrar panel" />
      <section
        ref={panel}
        onKeyDown={(event) => {
          if (!containFocus) return;
          if (event.key === "Escape") { event.preventDefault(); onClose(); }
          if (event.key !== "Tab") return;
          const elements = Array.from(panel.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]') ?? []);
          const first = elements[0], last = elements.at(-1);
          if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
          else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
        }}
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
