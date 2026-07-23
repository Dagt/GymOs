"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  getBranches,
  getCurrentOrganization,
  getOwnerDashboard,
  getStoredSession,
  type BranchSummary,
  type OrganizationSummary,
  type OwnerDashboard,
  type StoredSession,
} from "@/lib/api";

const operationCards = [
  { title: "Recepcion", copy: "Clientes, ventas, pagos y membresias del dia.", href: "/reception", label: "Abrir mostrador" },
  { title: "Reservas", copy: "Agenda, recursos, confirmaciones y pagos vinculados.", href: "/bookings", label: "Ver agenda" },
  { title: "Inventario", copy: "Stock, movimientos y articulos que requieren revision.", href: "/inventory", label: "Revisar stock" },
  { title: "Equipo", copy: "Usuarios internos, roles y accesos por sucursal.", href: "/users", label: "Administrar equipo" },
  { title: "Catalogo", copy: "Planes, productos y precios disponibles para caja.", href: "/catalog", label: "Ver catalogo" },
  { title: "Coaches", copy: "Rutinas, asignaciones y actividad de entrenamiento.", href: "/coaches", label: "Ver coaches" },
];

export default function OperationsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [dashboard, setDashboard] = useState<OwnerDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const [currentOrganization, availableBranches, ownerDashboard] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken),
          getOwnerDashboard(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(currentOrganization);
        setBranches(availableBranches);
        setDashboard(ownerDashboard);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar la operacion.");
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

  if (loading) {
    return <LoadingScreen text="Cargando operacion diaria..." />;
  }

  if (!session) {
    return (
      <AuthRequired
        message="La consola de gerencia requiere una cuenta interna con permisos de operacion."
        title="Inicia sesion para gestionar operacion"
      />
    );
  }

  const activeBranch = branches.find((branch) => branch.id === session.user.branchId) ?? null;

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Gerencia"
      organizationName={organization?.tradeName}
      session={session}
      subtitle="Pendientes, herramientas y decisiones operativas para mantener el gimnasio funcionando."
      title="Operacion diaria"
    >
      {error ? <InlineError message={error} /> : null}

      <section className="operations-grid">
        <section className="operations-main">
          <section className="operations-hero">
            <div>
              <p className="app-eyebrow">Prioridades</p>
              <h2>Empieza por lo que mueve el dia</h2>
              <span>Ventas, reservas, inventario, equipo y coaches estan separados para que cada encargado vaya directo a su trabajo.</span>
            </div>
            <div className="operations-kpis">
              <Metric label="Ventas" value={dashboard?.saleCount ?? 0} />
              <Metric label="Reservas" value={dashboard?.bookingCount ?? 0} />
              <Metric label="Inventario bajo" value={dashboard?.lowStockItems ?? 0} />
            </div>
          </section>

          <section className="operations-card-grid">
            {operationCards.map((card) => (
              <Link className="operations-card" href={card.href} key={card.href}>
                <strong>{card.title}</strong>
                <span>{card.copy}</span>
                <b>{card.label}</b>
              </Link>
            ))}
          </section>
        </section>

        <aside className="operations-side">
          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Sucursal activa</p>
                <h2>{activeBranch?.name ?? "Sin sucursal"}</h2>
              </div>
            </div>
            <p className="admin-muted-text">
              La operacion diaria se calcula sobre la sucursal activa. Cambia de sucursal desde administracion de gimnasios o configuracion.
            </p>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Salud del negocio</p>
                <h2>Senales rapidas</h2>
              </div>
            </div>
            <div className="owner-mini-grid">
              <MiniMetric label="Membresias activas" value={dashboard?.activeMemberships ?? 0} />
              <MiniMetric label="Clientes nuevos" value={dashboard?.newClients ?? 0} />
              <MiniMetric label="Rutinas activas" value={dashboard?.activeWorkoutAssignments ?? 0} />
              <MiniMetric label="Avisos en cola" value={dashboard?.queuedNotifications ?? 0} />
            </div>
          </section>
        </aside>
      </section>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <article>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function MiniMetric({ label, value }: { label: string; value: number }) {
  return (
    <div className="owner-mini-metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
