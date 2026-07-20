"use client";

import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  clearSession,
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
  {
    title: "Reservas",
    detail: "Agenda recursos, registra reservas y vincula pagos cuando aplique.",
    href: "/bookings",
    label: "Ver agenda",
    accent: "bookings",
  },
  {
    title: "Beneficios",
    detail: "Consulta beneficios aplicables para socios y promociones activas.",
    href: "/benefits",
    label: "Ver beneficios",
    accent: "benefits",
  },
];

const daySignals = [
  { label: "Caja", value: "Lista", tone: "green" },
  { label: "Pagos", value: "Por revisar", tone: "amber" },
  { label: "Sucursal", value: "Activa", tone: "blue" },
];

export default function ReceptionPage() {
  const router = useRouter();
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
        let effectiveSession: StoredSession = storedSession;
        const onlyBranch = availableBranches.length === 1 ? availableBranches[0] : null;
        if (!storedSession.user.branchId && onlyBranch) {
          const nextSession = await selectBranch(storedSession.accessToken, onlyBranch.id);
          storeSession(nextSession);
          effectiveSession = {
            accessToken: nextSession.accessToken,
            refreshToken: nextSession.refreshToken,
            user: nextSession.user,
          };
        }
        const currentOrganization = await getCurrentOrganization(effectiveSession.accessToken).catch(() => null);
        if (!active) {
          return;
        }
        setSession(effectiveSession);
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
  const receptionOrganizationName =
    organization?.tradeName ?? session?.user.brandName ?? session?.user.organizationName ?? "GymOS";

  function logout() {
    clearSession();
    router.push("/login");
  }

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
      hideSidebar={canOperate}
      mode={canOperate ? "operation" : undefined}
      organizationName={receptionOrganizationName}
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
        <section className="reception-home">
          <section className="reception-identity-panel">
            {session.user.logoUrl ? (
              <Image alt={session.user.brandName ?? receptionOrganizationName} className="reception-brand-mark image" height={72} src={session.user.logoUrl} unoptimized width={72} />
            ) : (
              <div className="reception-brand-mark" aria-hidden="true">
                {(session.user.brandName ?? receptionOrganizationName).slice(0, 1)}
              </div>
            )}
            <div>
              <p>{receptionOrganizationName}</p>
              <h2>{visibleBranchName}</h2>
              <span>
                Entraste como Recepcion. Usuario: {session.user.fullName} ({session.user.username}).
              </span>
            </div>
            <div className="reception-scope-card">
              <span>Alcance</span>
              <strong>Mostrador</strong>
              <small>Clientes, ventas, pagos, membresias y reservas de la sucursal activa.</small>
            </div>
            <button onClick={logout} type="button">
              Salir
            </button>
          </section>

          <section className="reception-command-layout">
            <section className="reception-action-rail" aria-label="Acciones de recepcion">
              <Link className="reception-primary-command" href="/pos">
                <span>Caja lista</span>
                <strong>Cobrar venta o membresia</strong>
                <p>Productos, planes, renovaciones y pagos manuales desde mostrador.</p>
                <b>Abrir caja</b>
              </Link>
              <div className="reception-secondary-commands">
                {focusActions.slice(1).map((action) => (
                  <Link className={`reception-command ${action.accent}`} href={action.href} key={action.href}>
                    <span>{action.title}</span>
                    <strong>{action.label}</strong>
                  </Link>
                ))}
              </div>
            </section>

            <aside className="reception-shift-panel">
              <div>
                <p>Turno actual</p>
                <h2>Mostrador en operacion</h2>
              </div>
              <div className="reception-signal-list">
                {daySignals.map((signal) => (
                  <ReportMetric key={signal.label} label={signal.label} value={signal.value} tone={signal.tone} />
                ))}
              </div>
              <div className="reception-next-panel">
                <span>Siguiente accion</span>
                <strong>Buscar socio o cobrar membresia</strong>
              </div>
            </aside>

            <section className="reception-wide-strip">
              <div>
                <p>Operacion rapida</p>
                <h2>Reservas, beneficios y ventas sin salir del flujo de mostrador</h2>
              </div>
              <div>
                <Link href="/bookings">Agenda</Link>
                <Link href="/benefits">Beneficios</Link>
                <Link href="/reports/sales">Ventas</Link>
              </div>
            </section>
          </section>
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
