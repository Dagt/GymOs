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
    title: "Cobrar o renovar",
    detail: "Venta de productos, mensualidades, renovaciones y pagos manuales.",
    href: "/pos",
    label: "Abrir punto de venta",
    accent: "money",
  },
  {
    title: "Socios",
    detail: "Buscar, registrar, consultar membresía y actualizar datos.",
    href: "/clients",
    label: "Gestionar socios",
    accent: "people",
  },
  {
    title: "Reportes del día",
    detail: "Ventas, pagos pendientes, saldos y actividad reciente.",
    href: "/reports/sales",
    label: "Ver ventas",
    accent: "report",
  },
  {
    title: "Kiosco para socios",
    detail: "Pantalla táctil para pagos, renovaciones y cambios de plan.",
    href: "/kiosk/KIOSK-MAIN",
    label: "Abrir pantalla",
    accent: "kiosk",
  },
];

const adminShortcuts = [
  { label: "Catálogo", href: "/catalog" },
  { label: "Beneficios", href: "/benefits" },
  { label: "Marca y datos", href: "/settings/organization" },
  { label: "Preparar gimnasio", href: "/onboarding" },
  { label: "Importar clientes", href: "/clients/import" },
];

const operationGroups = [
  {
    title: "Operación",
    items: [
      { label: "Control de acceso", href: "/access" },
      { label: "Reservas", href: "/bookings" },
      { label: "Inventario", href: "/inventory" },
    ],
  },
  {
    title: "Servicios",
    items: [
      { label: "Coaches", href: "/coaches" },
      { label: "Bienestar", href: "/wellness" },
      { label: "Automatizaciones", href: "/automations" },
    ],
  },
];

const navigation = [
  { label: "Inicio", href: "/", hint: "Entrada" },
  { label: "Panel", href: "/reception", hint: "Administración" },
  { label: "Caja", href: "/pos", hint: "Cobros" },
  { label: "Socios", href: "/clients", hint: "Clientes" },
  { label: "Reportes", href: "/dashboard", hint: "Dueño" },
];

const daySignals = [
  { label: "Caja", value: "Lista", tone: "green" },
  { label: "Pagos", value: "Pendientes", tone: "amber" },
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
          <h1>Inicia sesión para administrar el gimnasio</h1>
          <span>El panel carga tu sucursal, tus permisos y las acciones disponibles para tu rol.</span>
          <Link href="/login">Iniciar sesión</Link>
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

        <nav aria-label="Navegación de administración">
          {navigation.map((item) => (
            <Link href={item.href} key={item.href}>
              <strong>{item.label}</strong>
              <span>{item.hint}</span>
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
            <p>Panel de administración</p>
            <h1>Operación del gimnasio</h1>
            <span>
              Accesos rápidos para mostrador, administración y dirección. Todo organizado por tarea, no por módulo técnico.
            </span>
          </div>
          <div className="admin-command-actions">
            <label className="admin-search-box">
              <span>Buscar</span>
              <input placeholder="Socio, venta o acción" />
            </label>
            <Link href="/reports/sales">Ventas</Link>
            <Link href="/settings/organization">Personalizar</Link>
          </div>
        </header>

        {error ? <p className="admin-alert">{error}</p> : null}

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
                <Link href="/dashboard">Dashboard completo</Link>
              </div>
              <div className="admin-report-strip">
                <ReportMetric label="Ventas del dia" value="$0.00" tone="green" />
                <ReportMetric label="Pagos pendientes" value="Por revisar" tone="amber" />
                <ReportMetric label="Membresias" value="Activas" tone="blue" />
              </div>
            </section>

            <details className="admin-panel admin-details">
              <summary>Más herramientas</summary>
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
                  <h2>Configuración frecuente</h2>
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
