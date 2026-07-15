"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
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
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/reception">Recepcion</Link>
            <h1 className="mt-1 text-3xl font-semibold">Dashboard del dueño</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">Salud operativa y comercial de la sucursal activa.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setFromDate(event.target.value)} type="date" value={fromDate} />
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setToDate(event.target.value)} type="date" value={toDate} />
            <button className="h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white" onClick={refresh} type="button">Actualizar</button>
          </div>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6">
        {error ? <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">{error}</p> : null}
        {dashboard ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
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
    </main>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <article className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <p className="text-sm font-medium text-[#5d6b63]">{label}</p>
      <p className="mt-2 text-3xl font-semibold">{value}</p>
    </article>
  );
}

function Centered({ text }: { text: string }) {
  return <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]"><p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">{text}</p></main>;
}

function AuthRequired() {
  return <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]"><Link className="rounded-md bg-[#13251c] px-4 py-3 text-sm font-semibold text-white" href="/login">Iniciar sesion</Link></main>;
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}
