"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppNavigation } from "@/components/AppNavigation";
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
    return <Centered text="Cargando dashboard..." />;
  }

  if (!session) {
    return <AuthRequired />;
  }

  return (
    <main className="app-shell text-[#111815]">
      <div className="app-shell-inner">
        <header className="app-topbar">
          <div className="app-title-block">
            <p className="app-eyebrow">Direccion</p>
            <h1 className="app-title">Dashboard del dueno</h1>
            <p className="app-subtitle">Salud operativa y comercial de la sucursal activa.</p>
          </div>
          <div className="app-actions">
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setFromDate(event.target.value)} type="date" value={fromDate} />
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setToDate(event.target.value)} type="date" value={toDate} />
            <button className="app-button" onClick={refresh} type="button">Actualizar</button>
          </div>
        </header>

        <AppNavigation />

        <section className="grid gap-6">
          {error ? <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">{error}</p> : null}
          {dashboard ? (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
              <Metric label="Ingresos pagados" value={formatMoney(dashboard.paidRevenueCents, "MXN")} />
              <Metric label="Ventas" value={dashboard.saleCount.toString()} />
              <Metric label="Clientes nuevos" value={dashboard.newClients.toString()} />
              <Metric label="Membresias activas" value={dashboard.activeMemberships.toString()} />
              <Metric label="Reservas" value={dashboard.bookingCount.toString()} />
              <Metric label="Inventario bajo" value={dashboard.lowStockItems.toString()} />
              <Metric label="Rutinas activas" value={dashboard.activeWorkoutAssignments.toString()} />
              <Metric label="Profesionales activos" value={dashboard.activeProfessionals.toString()} />
              <Metric label="Notificaciones" value={dashboard.queuedNotifications.toString()} />
              <Metric label="Automatizaciones" value={dashboard.enabledAutomations.toString()} />
            </div>
          ) : null}
        </section>
      </div>
    </main>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <article className="app-card p-5">
      <p className="text-sm font-bold text-[#5d6b63]">{label}</p>
      <p className="mt-2 text-3xl font-black">{value}</p>
    </article>
  );
}

function Centered({ text }: { text: string }) {
  return (
    <main className="grid min-h-screen place-items-center px-6 text-[#111815]">
      <p className="app-card px-4 py-3 text-sm text-[#42514a]">{text}</p>
    </main>
  );
}

function AuthRequired() {
  return (
    <main className="grid min-h-screen place-items-center px-6 text-[#111815]">
      <Link className="app-button" href="/login">Iniciar sesion</Link>
    </main>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}
