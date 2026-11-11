"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";

export type AppNoticeTone = "error" | "info" | "success";

export function AppNotice({
  message,
  onClose,
  title,
  tone,
  inline = false,
}: {
  message: string;
  onClose?: () => void;
  title?: string;
  tone: AppNoticeTone;
  inline?: boolean;
}) {
  const [dismissedMessage, setDismissedMessage] = useState<string | null>(null);
  const region = typeof document === "undefined" ? null : document.getElementById("app-notice-region");

  useEffect(() => {
    if (tone !== "success") return;
    const timeout = window.setTimeout(() => setDismissedMessage(message), 6000);
    return () => window.clearTimeout(timeout);
  }, [message, tone]);

  if (dismissedMessage === message || (!inline && !region)) return null;

  const resolvedTitle = title ?? ({
    error: "No pudimos completar la acción",
    info: "Información",
    success: "Listo",
  } as const)[tone];

  function close() {
    setDismissedMessage(message);
    onClose?.();
  }

  const notice = (
    <aside
      aria-live={tone === "error" ? "assertive" : "polite"}
      className={`app-notice app-notice-${tone}`}
      role={tone === "error" ? "alert" : "status"}
    >
      <span aria-hidden="true" className="app-notice-icon">
        {tone === "success" ? "✓" : tone === "error" ? "!" : "i"}
      </span>
      <div>
        <strong>{resolvedTitle}</strong>
        <p>{message}</p>
      </div>
      <button aria-label="Cerrar mensaje" onClick={close} type="button">×</button>
    </aside>
  );
  return inline ? notice : createPortal(notice, region!);
}
