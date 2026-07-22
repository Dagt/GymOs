"use client";

import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  getOwnerDashboard,
  getStoredSession,
  type OwnerDashboard,
  type StoredSession,
} from "@/lib/api";

export default function OwnerDashboardPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [dashboard, setDashboard] = useState<OwnerDashboard | null>(null);
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!active) {
        return;
      }
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const loaded = await getOwnerDashboard(stored.accessToken);
        if (active) {
          setDashboard(loaded);
          setFromDate(loaded.fromDate);
          setToDate(loaded.toDate);
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar dashboard.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, []);

  async function refresh() {
    if (!session) {
      return;
    }
    setError(null);
    try {
      setDashboard(await getOwnerDashboard(session.accessToken, fromDate, toDate));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar dashboard.");
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando panel ejecutivo..." />;
  }

  if (!session) {
    return <AuthRequired />;
  }

  return (
    <AppShell
      eyebrow="Gerencia"
      session={session}
      subtitle="Indicadores claros para revisar ingresos, membresías, operación diaria y pendientes de la sucursal activa."
      title="Panel ejecutivo"
    >
      <section className="grid gap-6">
        <section className="admin-panel">
          <div className="admin-panel-header">
            <div>
              <p>Periodo</p>
              <h2>Filtrar indicadores</h2>
            </div>
          </div>
          <div className="admin-command-actions justify-start">
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setFromDate(event.target.value)} type="date" value={fromDate} />
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setToDate(event.target.value)} type="date" value={toDate} />
            <button className="app-button" onClick={refresh} type="button">Actualizar</button>
          </div>
        </section>

        {error ? <InlineError message={error} /> : null}

        {dashboard ? (
          <>
            <section className="owner-summary-grid">
              <Metric
                accent="green"
                helper="Pagos confirmados en el periodo"
                label="Ingresos cobrados"
                value={formatMoney(dashboard.paidRevenueCents, "MXN")}
              />
              <Metric
                accent="blue"
                helper="Tickets y renovaciones registradas"
                label="Ventas creadas"
                value={dashboard.saleCount.toString()}
              />
              <Metric
                accent="amber"
                helper="Socios con acceso vigente"
                label="Membresías activas"
                value={dashboard.activeMemberships.toString()}
              />
            </section>

            <section className="owner-section-grid">
              <article className="owner-panel">
                <div>
                  <p className="app-eyebrow">Crecimiento</p>
                  <h2>Clientes y servicios</h2>
                </div>
                <div className="owner-mini-grid">
                  <MiniMetric label="Clientes nuevos" value={dashboard.newClients.toString()} />
                  <MiniMetric label="Reservas" value={dashboard.bookingCount.toString()} />
                  <MiniMetric label="Rutinas activas" value={dashboard.activeWorkoutAssignments.toString()} />
                  <MiniMetric label="Profesionales activos" value={dashboard.activeProfessionals.toString()} />
                </div>
              </article>

              <article className="owner-panel">
                <div>
                  <p className="app-eyebrow">Pendientes</p>
                  <h2>Operación por revisar</h2>
                </div>
                <div className="owner-mini-grid">
                  <MiniMetric label="Inventario bajo" tone={dashboard.lowStockItems > 0 ? "warning" : "ok"} value={dashboard.lowStockItems.toString()} />
                  <MiniMetric label="Notificaciones en cola" value={dashboard.queuedNotifications.toString()} />
                  <MiniMetric label="Automatizaciones activas" value={dashboard.enabledAutomations.toString()} />
                </div>
              </article>
            </section>
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

function Metric({
  accent,
  helper,
  label,
  value,
}: {
  accent: "green" | "blue" | "amber";
  helper: string;
  label: string;
  value: string;
}) {
  return (
    <article className={`owner-metric ${accent}`}>
      <p>{label}</p>
      <strong>{value}</strong>
      <span>{helper}</span>
    </article>
  );
}

function MiniMetric({ label, tone, value }: { label: string; tone?: "ok" | "warning"; value: string }) {
  return (
    <div className={`owner-mini-metric ${tone ?? ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="El panel administrativo requiere una cuenta con permisos de dirección o administración."
      title="Inicia sesión para ver indicadores"
    />
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}
