"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  getBranches,
  getCurrentOrganization,
  getStoredSession,
  selectBranch,
  storeSession,
  type BranchSummary,
  type OrganizationSummary,
  type StoredSession,
} from "@/lib/api";

const focusActions = [
  {
    title: "Cobrar venta o membresía",
    detail: "Productos, planes, renovaciones y pagos manuales desde caja.",
    href: "/pos",
    label: "Abrir caja",
    accent: "money",
  },
  {
    title: "Buscar o registrar socio",
    detail: "Alta rápida, ficha, membresías, beneficios y notas.",
    href: "/clients",
    label: "Ir a socios",
    accent: "people",
  },
  {
    title: "Revisar ventas",
    detail: "Pagos pendientes, saldos, comprobantes y operaciones recientes.",
    href: "/reports/sales",
    label: "Ver reporte",
    accent: "report",
  },
  {
    title: "Pantalla para socios",
    detail: "Autoservicio para pagos, renovaciones y cambios de plan.",
    href: "/kiosk/KIOSK-MAIN",
    label: "Abrir kiosco",
    accent: "kiosk",
  },
];

const operationGroups = [
  {
    title: "Operación física",
    items: [
      { label: "Control de acceso", href: "/access" },
      { label: "Reservas", href: "/bookings" },
      { label: "Inventario", href: "/inventory" },
    ],
  },
  {
    title: "Servicios y comunicación",
    items: [
      { label: "Coaches", href: "/coaches" },
      { label: "Bienestar", href: "/wellness" },
      { label: "Automatizaciones", href: "/automations" },
    ],
  },
];

const daySignals = [
  { label: "Caja", value: "Lista", tone: "green" },
  { label: "Pagos", value: "Por revisar", tone: "amber" },
  { label: "Kiosco", value: "Disponible", tone: "blue" },
];

export default function ReceptionPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [switchingBranchId, setSwitchingBranchId] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadReception() {
      const storedSession = getStoredSession();
      if (!active) {
        return;
      }

      if (!storedSession) {
        setLoading(false);
        return;
      }

      setSession(storedSession);

      try {
        const [currentOrganization, availableBranches] = await Promise.all([
          getCurrentOrganization(storedSession.accessToken),
          getBranches(storedSession.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(currentOrganization);
        setBranches(availableBranches);
      } catch (exception) {
        if (!active) {
          return;
        }
        setError(exception instanceof Error ? exception.message : "No fue posible cargar recepción.");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadReception();

    return () => {
      active = false;
    };
  }, []);

  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;
  const canOperate = Boolean(activeBranch);

  async function switchBranch(branchId: string) {
    if (!session || branchId === session.user.branchId) {
      return;
    }

    setSwitchingBranchId(branchId);
    setError(null);
    try {
      const nextSession = await selectBranch(session.accessToken, branchId);
      storeSession(nextSession);
      setSession({
        accessToken: nextSession.accessToken,
        refreshToken: nextSession.refreshToken,
        user: nextSession.user,
      });
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar sucursal.");
    } finally {
      setSwitchingBranchId(null);
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando panel operativo..." />;
  }

  if (!session) {
    return (
      <AuthRequired
        message="El panel carga tu sucursal, tus permisos y las acciones disponibles para tu rol."
        title="Inicia sesión para administrar el gimnasio"
      />
    );
  }

  return (
    <AppShell
      actions={[
        ...(canOperate ? [{ href: "/reports/sales", label: "Ventas" }] : []),
      ]}
      branchName={activeBranch?.name}
      eyebrow="Recepcion"
      organizationName={organization?.tradeName}
      session={session}
      subtitle={
        canOperate
          ? "Clientes, ventas, pagos, membresias y reservas de la sucursal activa."
          : "Selecciona una sucursal para operar recepcion."
      }
      title={canOperate ? "Mostrador" : "Recepcion sin sucursal"}
    >
      {error ? <InlineError message={error} /> : null}

      {!canOperate ? (
        <section className="admin-content-grid">
          <section className="admin-main-column">
            <section className="admin-panel">
              <div className="admin-panel-header">
                <div>
                  <p>Siguiente paso</p>
                  <h2>Empieza por lo indispensable</h2>
                </div>
              </div>
              <div className="admin-setup-steps">
                <Link href="/onboarding">1. Alta o preparación del gimnasio</Link>
                <Link href="/settings/organization">2. Datos, marca y primera sucursal</Link>
              </div>
              <details className="admin-advanced-setup">
                <summary>Opciones avanzadas para apoyar al dueño</summary>
                <p>
                  Úsalas sólo si quieres dejar cargada información inicial. El dueño o gerente puede completarla después.
                </p>
                <div>
                  <Link href="/catalog">Catálogo de planes y productos</Link>
                  <Link href="/clients/import">Importar clientes iniciales</Link>
                </div>
              </details>
            </section>
          </section>

          <aside className="admin-context-column">
            <section className="admin-panel">
              <div className="admin-panel-header">
                <div>
                  <p>Estado</p>
                  <h2>Sin sucursal activa</h2>
                </div>
              </div>
              <p className="admin-muted-text">
                Cuando configures una sucursal, GymOS mostrará caja, socios, membresías, kiosco y reportes operativos
                para el equipo del gimnasio.
              </p>
            </section>
          </aside>
        </section>
      ) : (
      <section className="admin-content-grid">
        <section className="admin-main-column">
          <section className="admin-status-board" aria-label="Estado operativo">
            {daySignals.map((signal) => (
              <ReportMetric key={signal.label} label={signal.label} value={signal.value} tone={signal.tone} />
            ))}
          </section>

          <section className="admin-focus-grid" aria-label="Acciones principales">
            {focusActions.map((action) => (
              <Link className={`admin-action-card ${action.accent}`} href={action.href} key={action.href}>
                <span>{action.title}</span>
                <p>{action.detail}</p>
                <strong>{action.label}</strong>
              </Link>
            ))}
          </section>

          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Reportes rápidos</p>
                <h2>Vista para decisiones</h2>
              </div>
              <Link href="/dashboard">Panel ejecutivo</Link>
            </div>
            <div className="admin-report-strip">
              <ReportMetric label="Ventas del día" value="$0.00" tone="green" />
              <ReportMetric label="Pagos pendientes" value="Por revisar" tone="amber" />
              <ReportMetric label="Membresías" value="Activas" tone="blue" />
            </div>
          </section>

          <details className="admin-panel admin-details">
            <summary>Más herramientas operativas</summary>
            <div className="admin-module-groups">
              {operationGroups.map((group) => (
                <section key={group.title}>
                  <h3>{group.title}</h3>
                  <div className="admin-link-grid">
                    {group.items.map((item) => (
                      <Link href={item.href} key={item.href}>
                        {item.label}
                      </Link>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          </details>
        </section>

        <aside className="admin-context-column">
          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Sucursal actual</p>
                <h2>{activeBranch?.name ?? "Selecciona una sucursal"}</h2>
              </div>
            </div>
            <div className="admin-branch-list">
              {branches.length === 0 ? (
                <p className="admin-muted-text">No se pudieron cargar sucursales. Revisa conexión o permisos.</p>
              ) : null}
              {branches.map((branch) => (
                <button
                  className={branch.id === session.user.branchId ? "active" : undefined}
                  disabled={switchingBranchId !== null}
                  key={branch.id}
                  onClick={() => switchBranch(branch.id)}
                  type="button"
                >
                  <strong>{branch.name}</strong>
                  <span>{branch.code}</span>
                </button>
              ))}
            </div>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Turno</p>
                <h2>Trabajo de mostrador</h2>
              </div>
            </div>
            <p className="admin-muted-text">
              Las opciones administrativas del gimnasio viven en las consolas de dueño o gerencia. Aqui solo aparecen tareas de atencion.
            </p>
          </section>
        </aside>
      </section>
      )}
    </AppShell>
  );
}

function ReportMetric({ label, value, tone }: { label: string; value: string; tone: string }) {
  return (
    <article className={`admin-report-metric ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}
