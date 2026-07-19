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
    title: "Cobrar venta o membresia",
    detail: "Productos, planes, renovaciones y pagos manuales desde caja.",
    href: "/pos",
    label: "Abrir caja",
    accent: "money",
  },
  {
    title: "Buscar o registrar socio",
    detail: "Alta rapida, ficha, membresias, beneficios y notas.",
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
    title: "Operacion fisica",
    items: [
      { label: "Reservas", href: "/bookings" },
      { label: "Beneficios", href: "/benefits" },
    ],
  },
  {
    title: "Consulta rapida",
    items: [
      { label: "Ventas recientes", href: "/reports/sales" },
      { label: "Socios", href: "/clients" },
    ],
  },
];

const daySignals = [
  { label: "Caja", value: "Lista", tone: "green" },
  { label: "Pagos", value: "Por revisar", tone: "amber" },
  { label: "Sucursal", value: "Activa", tone: "blue" },
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
      if (!storedSession) {
        setLoading(false);
        return;
      }

      setSession(storedSession);
      setError(null);
      try {
        const availableBranches = await getBranches(storedSession.accessToken);
        const currentOrganization = await getCurrentOrganization(storedSession.accessToken).catch(() => null);
        if (!active) {
          return;
        }
        setBranches(availableBranches);
        setOrganization(currentOrganization);
      } catch (exception) {
        if (!active) {
          return;
        }
        setError(exception instanceof Error ? exception.message : "No fue posible cargar recepcion.");
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
  const canOperate = Boolean(session?.user.branchId);
  const visibleBranchName = activeBranch?.name ?? (session?.user.branchId ? "Sucursal activa" : null);

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
    return <LoadingScreen text="Cargando panel de recepcion..." />;
  }

  if (!session) {
    return (
      <AuthRequired
        message="El panel carga tu sucursal, tus permisos y las acciones disponibles para tu rol."
        title="Inicia sesion para atender recepcion"
      />
    );
  }

  return (
    <AppShell
      actions={[]}
      branchName={visibleBranchName}
      eyebrow="Recepcion"
      organizationName={organization?.tradeName}
      session={session}
      subtitle={
        canOperate
          ? "Atajos de mostrador para clientes, ventas, pagos, membresias y reservas."
          : "Tu usuario no tiene una sucursal operativa activa."
      }
      title={canOperate ? "Dashboard de recepcion" : "Recepcion sin sucursal"}
    >
      {error && !canOperate ? <InlineError message={error} /> : null}

      {!canOperate ? (
        <ReceptionBranchSetup
          branches={branches}
          session={session}
          switchingBranchId={switchingBranchId}
          onSwitchBranch={switchBranch}
        />
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
                  <p>Turno actual</p>
                  <h2>Acciones frecuentes</h2>
                </div>
              </div>
              <div className="admin-report-strip">
                <ReportMetric label="Ventas del dia" value="$0.00" tone="green" />
                <ReportMetric label="Pagos pendientes" value="Por revisar" tone="amber" />
                <ReportMetric label="Socios" value="Atencion" tone="blue" />
              </div>
            </section>

            <details className="admin-panel admin-details">
              <summary>Mas herramientas de recepcion</summary>
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
                  <h2>{visibleBranchName}</h2>
                </div>
              </div>
              <div className="admin-branch-list">
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
                  <p>Alcance</p>
                  <h2>Mostrador</h2>
                </div>
              </div>
              <p className="admin-muted-text">
                Esta consola esta enfocada en atencion diaria. La administracion de gimnasios, sucursales y usuarios queda en dueno o gerencia.
              </p>
            </section>
          </aside>
        </section>
      )}
    </AppShell>
  );
}

function ReceptionBranchSetup({
  branches,
  session,
  switchingBranchId,
  onSwitchBranch,
}: {
  branches: BranchSummary[];
  session: StoredSession;
  switchingBranchId: string | null;
  onSwitchBranch: (branchId: string) => void;
}) {
  return (
    <section className="admin-content-grid">
      <section className="admin-main-column">
        <section className="admin-panel">
          <div className="admin-panel-header">
            <div>
              <p>Contexto operativo</p>
              <h2>Selecciona una sucursal permitida</h2>
            </div>
          </div>
          <p className="admin-muted-text">
            Recepcion no configura gimnasios. Si no aparece una sucursal, pide al dueno o gerente que revise tu acceso.
          </p>
          <div className="admin-branch-list">
            {branches.length === 0 ? (
              <p className="admin-muted-text">No tienes sucursales asignadas a este usuario.</p>
            ) : null}
            {branches.map((branch) => (
              <button
                className={branch.id === session.user.branchId ? "active" : undefined}
                disabled={switchingBranchId !== null}
                key={branch.id}
                onClick={() => onSwitchBranch(branch.id)}
                type="button"
              >
                <strong>{branch.name}</strong>
                <span>{branch.code}</span>
              </button>
            ))}
          </div>
        </section>
      </section>

      <aside className="admin-context-column">
        <section className="admin-panel">
          <div className="admin-panel-header">
            <div>
              <p>Acceso de recepcion</p>
              <h2>Mostrador</h2>
            </div>
          </div>
          <p className="admin-muted-text">
            Tu consola solo muestra tareas de mostrador. La administracion de gimnasios, sucursales y usuarios queda fuera de recepcion.
          </p>
        </section>
      </aside>
    </section>
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
