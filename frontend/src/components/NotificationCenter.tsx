"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { getNotifications, type NotificationLog, type StoredSession } from "@/lib/api";

const REFRESH_INTERVAL_MS = 60_000;

export function NotificationCenter({ session }: { session: StoredSession }) {
  const [notifications, setNotifications] = useState<NotificationLog[]>([]);
  const [open, setOpen] = useState(false);
  const canRead = session.user.permissions.includes("notification.read")
    || session.user.permissions.includes("platform.admin");

  useEffect(() => {
    if (!canRead) return;
    let active = true;
    let timer: number | undefined;

    async function load() {
      try {
        const result = await getNotifications(session.accessToken, 12);
        if (active) setNotifications(result);
      } catch {
        // The command bar must remain usable even if the notification feed is unavailable.
      }
      if (active) timer = window.setTimeout(load, REFRESH_INTERVAL_MS);
    }

    void load();
    return () => {
      active = false;
      if (timer) window.clearTimeout(timer);
    };
  }, [canRead, session.accessToken]);

  const importantCount = useMemo(
    () => notifications.filter((item) => isImportant(item.status)).length,
    [notifications],
  );

  if (!canRead) return null;

  return (
    <div className="notification-center">
      <button
        aria-expanded={open}
        aria-haspopup="dialog"
        className="notification-center-trigger"
        onClick={() => setOpen((value) => !value)}
        type="button"
      >
        <span>Avisos</span>
        {importantCount > 0 ? <strong aria-label={`${importantCount} avisos requieren atención`}>{importantCount}</strong> : null}
      </button>

      {open ? (
        <section aria-label="Avisos recientes" className="notification-center-popover" role="dialog">
          <header>
            <div>
              <p>Centro de avisos</p>
              <strong>Actividad reciente</strong>
            </div>
            <button aria-label="Cerrar avisos" onClick={() => setOpen(false)} type="button">×</button>
          </header>

          <div className="notification-center-list">
            {notifications.length === 0 ? <p className="notification-center-empty">No hay avisos recientes.</p> : null}
            {notifications.slice(0, 6).map((notification) => (
              <article key={notification.id}>
                <div>
                  <strong>{notification.subject || humanizeTrigger(notification.triggerType)}</strong>
                  <span className={`notification-status ${statusTone(notification.status)}`}>{humanizeStatus(notification.status)}</span>
                </div>
                <p>{notification.body}</p>
                <small>{formatDateTime(notification.sentAt || notification.createdAt)}</small>
              </article>
            ))}
          </div>

          <Link href="/notifications" onClick={() => setOpen(false)}>Ver todos los avisos</Link>
        </section>
      ) : null}
    </div>
  );
}

function isImportant(status: string) {
  return ["FAILED", "ERROR", "PENDING", "RETRY_SCHEDULED"].includes(status.toUpperCase());
}

function statusTone(status: string) {
  const normalized = status.toUpperCase();
  if (["FAILED", "ERROR"].includes(normalized)) return "error";
  if (["PENDING", "RETRY_SCHEDULED", "QUEUED"].includes(normalized)) return "warning";
  return "ok";
}

function humanizeStatus(status: string) {
  const values: Record<string, string> = {
    SENT: "Enviado",
    SUCCEEDED: "Completado",
    PENDING: "Pendiente",
    QUEUED: "En cola",
    FAILED: "Fallido",
    ERROR: "Error",
    RETRY_SCHEDULED: "Reintento",
  };
  return values[status.toUpperCase()] ?? status;
}

function humanizeTrigger(trigger: string | null) {
  if (!trigger) return "Aviso interno";
  return trigger.toLowerCase().replaceAll("_", " ").replace(/^./, (value) => value.toUpperCase());
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
