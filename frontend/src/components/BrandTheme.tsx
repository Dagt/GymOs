"use client";

import { FormEvent, useEffect, useState } from "react";

type BrandThemeConfig = {
  name: string;
  logoUrl: string;
  primary: string;
  accent: string;
  surface: string;
};

const defaultTheme: BrandThemeConfig = {
  name: "GymOS",
  logoUrl: "",
  primary: "#164E63",
  accent: "#F59E0B",
  surface: "#F4F7FB",
};

const storageKey = "gymos.brandTheme";

export function BrandTheme() {
  const [theme, setTheme] = useState<BrandThemeConfig>(() => readStoredTheme());
  const [draft, setDraft] = useState<BrandThemeConfig>(() => readStoredTheme());
  const [open, setOpen] = useState(false);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    window.localStorage.setItem(storageKey, JSON.stringify(draft));
    applyTheme(draft);
    setTheme(draft);
    setOpen(false);
  }

  function reset() {
    window.localStorage.removeItem(storageKey);
    applyTheme(defaultTheme);
    setTheme(defaultTheme);
    setDraft(defaultTheme);
  }

  return (
    <>
      <div aria-hidden="true" className="brand-watermark">
        {theme.logoUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img alt="" src={theme.logoUrl} />
        ) : (
          <span>{initials(theme.name)}</span>
        )}
      </div>
      <button
        aria-label="Personalizar marca"
        className="brand-customizer-trigger"
        onClick={() => setOpen((current) => !current)}
        type="button"
      >
        <span className="brand-customizer-dot" />
        Marca
      </button>
      {open ? (
        <aside className="brand-customizer-panel" role="dialog" aria-label="Personalizacion de marca">
          <div className="brand-customizer-header">
            <div>
              <p>Personalizacion</p>
              <h2>Marca del gimnasio</h2>
            </div>
            <button aria-label="Cerrar" onClick={() => setOpen(false)} type="button">
              x
            </button>
          </div>
          <form className="brand-customizer-form" onSubmit={submit}>
            <label>
              Nombre visible
              <input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
            </label>
            <label>
              URL del logo
              <input
                placeholder="https://..."
                value={draft.logoUrl}
                onChange={(event) => setDraft({ ...draft, logoUrl: event.target.value })}
              />
            </label>
            <div className="brand-color-grid">
              <label>
                Principal
                <input type="color" value={draft.primary} onChange={(event) => setDraft({ ...draft, primary: event.target.value })} />
              </label>
              <label>
                Acento
                <input type="color" value={draft.accent} onChange={(event) => setDraft({ ...draft, accent: event.target.value })} />
              </label>
              <label>
                Fondo
                <input type="color" value={draft.surface} onChange={(event) => setDraft({ ...draft, surface: event.target.value })} />
              </label>
            </div>
            <div className="brand-preview">
              <div className="brand-preview-mark">
                {draft.logoUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img alt="" src={draft.logoUrl} />
                ) : (
                  <span>{initials(draft.name)}</span>
                )}
              </div>
              <div>
                <p>{draft.name || "GymOS"}</p>
                <span>Vista previa de marca</span>
              </div>
            </div>
            <div className="brand-customizer-actions">
              <button type="button" onClick={reset}>
                Restablecer
              </button>
              <button type="submit">Guardar</button>
            </div>
          </form>
        </aside>
      ) : null}
    </>
  );
}

function applyTheme(theme: BrandThemeConfig) {
  const root = document.documentElement;
  root.style.setProperty("--brand-name", `"${theme.name || "GymOS"}"`);
  root.style.setProperty("--brand-primary", theme.primary || defaultTheme.primary);
  root.style.setProperty("--brand-accent", theme.accent || defaultTheme.accent);
  root.style.setProperty("--brand-surface", theme.surface || defaultTheme.surface);
}

function safeParse(value: string): BrandThemeConfig {
  try {
    return { ...defaultTheme, ...(JSON.parse(value) as Partial<BrandThemeConfig>) };
  } catch {
    return defaultTheme;
  }
}

function readStoredTheme(): BrandThemeConfig {
  if (typeof window === "undefined") {
    return defaultTheme;
  }
  const stored = window.localStorage.getItem(storageKey);
  return stored ? safeParse(stored) : defaultTheme;
}

function initials(value: string) {
  return (value || "GymOS")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}
