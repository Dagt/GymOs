"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  clearSession,
  getBranches,
  getCurrentOrganization,
  getStoredSession,
  logout as logoutRequest,
  selectBranch,
  storeSession,
  type BranchSummary,
  type CurrentUser,
  type OrganizationSummary,
  type StoredSession,
} from "@/lib/api";

const focusActions = [
  {
    title: "Cobrar venta",
    detail: "Productos, planes o pagos manuales.",
    href: "/pos",
    label: "Abrir caja",
    accent: "money",
  },
  {
    title: "Buscar o registrar socio",
    detail: "Alta rapida, ficha y membresias.",
    href: "/clients",
    label: "Ir a socios",
    accent: "people",
  },
  {
    title: "Revisar ventas",
    detail: "Pagos, saldos y operaciones recientes.",
    href: "/reports/sales",
    label: "Ver reporte",
    accent: "report",
  },
  {
    title: "Abrir autoservicio",
    detail: "Pantalla de pagos y renovaciones.",
    href: "/kiosk/KIOSK-MAIN",
    label: "Abrir kiosco",
    accent: "kiosk",
  },
];

const adminShortcuts = [
  { label: "Catalogo", href: "/catalog" },
  { label: "Beneficios", href: "/benefits" },
  { label: "Configuracion", href: "/settings/organization" },
  { label: "Onboarding", href: "/onboarding" },
  { label: "Importar clientes", href: "/clients/import" },
];

const operationShortcuts = [
  { label: "Accesos", href: "/access" },
  { label: "Reservas", href: "/bookings" },
  { label: "Inventario", href: "/inventory" },
  { label: "Coaches", href: "/coaches" },
  { label: "Bienestar", href: "/wellness" },
  { label: "Automatizaciones", href: "/automations" },
];

const navigation = [
  { label: "Inicio", href: "/" },
  { label: "Recepcion", href: "/reception" },
  { label: "Caja", href: "/pos" },
  { label: "Socios", href: "/clients" },
  { label: "Reportes", href: "/dashboard" },
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

  function logout() {
    if (session) {
      void logoutRequest(session.accessToken, session.refreshToken).catch(() => undefined);
    }
    clearSession();
    setSession(null);
    setOrganization(null);
    setBranches([]);
  }

  if (loading) {
    return <Centered text="Cargando panel..." />;
  }

  if (!session) {
    return (
      <main className="admin-empty-state">
        <section>
          <p>GymOS</p>
          <h1>Inicia sesion para administrar el gimnasio</h1>
          <span>El panel carga tu sucursal, tus permisos y las acciones disponibles para tu rol.</span>
          <Link href="/login">Iniciar sesion</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="admin-console">
      <aside className="admin-sidebar">
        <div className="admin-sidebar-brand">
          <span>G</span>
          <div>
            <strong>{organization?.tradeName ?? "GymOS"}</strong>
            <small>{activeBranch?.name ?? "Sin sucursal"}</small>
          </div>
        </div>

        <nav aria-label="Navegacion de administracion">
          {navigation.map((item) => (
            <Link href={item.href} key={item.href}>
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="admin-sidebar-footer">
          <span>{session.user.fullName}</span>
          <button onClick={logout} type="button">
            Salir
          </button>
        </div>
      </aside>

      <section className="admin-workspace">
        <header className="admin-command-bar">
          <div>
            <p>Panel de administracion</p>
            <h1>Que quieres hacer ahora?</h1>
            <span>
              Acciones principales al frente. Los modulos menos frecuentes estan agrupados para no saturar la pantalla.
            </span>
          </div>
          <div className="admin-command-actions">
            <Link href="/reports/sales">Ventas</Link>
            <Link href="/settings/organization">Personalizar</Link>
          </div>
        </header>

        {error ? <p className="admin-alert">{error}</p> : null}

        <section className="admin-content-grid">
          <section className="admin-main-column">
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
                  <p>Reportes rapidos</p>
                  <h2>Vista para decisiones</h2>
                </div>
                <Link href="/dashboard">Dashboard completo</Link>
              </div>
              <div className="admin-report-strip">
                <ReportMetric label="Ventas del dia" value="$0.00" tone="green" />
                <ReportMetric label="Pagos pendientes" value="Por revisar" tone="amber" />
                <ReportMetric label="Membresias" value="Activas" tone="blue" />
              </div>
            </section>

            <details className="admin-panel admin-details">
              <summary>Mas herramientas operativas</summary>
              <div className="admin-link-grid">
                {operationShortcuts.map((item) => (
                  <Link href={item.href} key={item.href}>
                    {item.label}
                  </Link>
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
                  <p>Administracion</p>
                  <h2>Configuracion frecuente</h2>
                </div>
              </div>
              <div className="admin-link-grid single">
                {adminShortcuts.map((item) => (
                  <Link href={item.href} key={item.href}>
                    {item.label}
                  </Link>
                ))}
              </div>
            </section>

            <details className="admin-panel admin-details">
              <summary>Permisos del usuario</summary>
              <PermissionList user={session.user} />
            </details>
          </aside>
        </section>
      </section>
    </main>
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

function PermissionList({ user }: { user: CurrentUser }) {
  if (user.permissions.length === 0) {
    return <p className="admin-muted-text">Sin permisos asignados.</p>;
  }

  return (
    <div className="admin-permission-list">
      {user.permissions.map((permission) => (
        <span key={permission}>{permission}</span>
      ))}
    </div>
  );
}

function Centered({ text }: { text: string }) {
  return (
    <main className="admin-empty-state">
      <section>
        <p>GymOS</p>
        <h1>{text}</h1>
      </section>
    </main>
  );
}
