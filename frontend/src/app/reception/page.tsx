"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { AppNavigation } from "@/components/AppNavigation";
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

const primaryActions = [
  {
    title: "Nueva venta",
    detail: "Cobrar productos, planes o servicios con pagos manuales.",
    href: "/pos",
    action: "Abrir POS",
  },
  {
    title: "Registrar cliente",
    detail: "Alta rapida, busqueda y ficha completa del cliente.",
    href: "/clients",
    action: "Ir a clientes",
  },
  {
    title: "Renovar en kiosco",
    detail: "Modo autoservicio para pagos, cambios de plan y membresias.",
    href: "/kiosk/KIOSK-MAIN",
    action: "Abrir kiosco",
  },
];

const managementActions = [
  { title: "Catalogo", detail: "Productos, planes, beneficios y servicios.", href: "/catalog" },
  { title: "Reporte de ventas", detail: "Saldos, cobros, pagos pendientes y ventas recientes.", href: "/reports/sales" },
  { title: "Dashboard", detail: "Indicadores ejecutivos para direccion.", href: "/dashboard" },
  { title: "Configuracion", detail: "Organizacion, sucursales y datos comerciales.", href: "/settings/organization" },
  { title: "Importar clientes", detail: "Carga inicial CSV con validaciones.", href: "/clients/import" },
  { title: "Onboarding", detail: "Checklist comercial y preparacion de piloto.", href: "/onboarding" },
];

const operationModules = [
  { title: "Accesos", detail: "Entradas, pases temporales y bitacora.", href: "/access" },
  { title: "Reservas", detail: "Recursos, agenda y check-in.", href: "/bookings" },
  { title: "Inventario", detail: "Existencias, movimientos y alertas.", href: "/inventory" },
  { title: "Coaches", detail: "Entrenadores, rutinas y asignaciones.", href: "/coaches" },
  { title: "Bienestar", detail: "Servicios profesionales y disponibilidad.", href: "/wellness" },
  { title: "Automatizaciones", detail: "Reglas, ejecuciones y notificaciones.", href: "/automations" },
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
  const permissionCount = useMemo(() => session?.user.permissions.length ?? 0, [session]);

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
    return <Centered text="Cargando recepcion..." />;
  }

  if (!session) {
    return (
      <main className="grid min-h-screen place-items-center px-6 text-[#111815]">
        <section className="app-card w-full max-w-md p-6">
          <p className="app-eyebrow">GymOS</p>
          <h1 className="mt-3 text-2xl font-black">Necesitas iniciar sesion</h1>
          <p className="mt-3 app-card-copy">
            La recepcion requiere una sesion activa para cargar organizacion, permisos y sucursales disponibles.
          </p>
          <Link className="mt-5 app-button" href="/login">
            Iniciar sesion
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell text-[#111815]">
      <div className="app-shell-inner">
        <header className="app-topbar">
          <div className="app-title-block">
            <p className="app-eyebrow">Recepcion</p>
            <h1 className="app-title">{organization?.tradeName ?? "GymOS"}</h1>
            <p className="app-subtitle">
              {activeBranch ? activeBranch.name : "Selecciona una sucursal para operar"} - {session.user.fullName}
            </p>
          </div>
          <div className="app-actions">
            <span className="app-pill">{permissionCount} permisos</span>
            <button className="app-button-secondary" onClick={logout} type="button">
              Salir
            </button>
          </div>
        </header>

        <AppNavigation />

        {error ? (
          <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
            {error}
          </p>
        ) : null}

        <section className="grid gap-5 lg:grid-cols-[0.9fr_1.45fr]">
          <aside className="app-card p-5">
            <p className="app-eyebrow">Contexto operativo</p>
            <h2 className="mt-2 app-card-title">{organization?.legalName ?? "Sin organizacion"}</h2>
            <dl className="mt-5 grid gap-4 text-sm">
              <InfoRow label="Moneda" value={organization?.defaultCurrency ?? "N/A"} />
              <InfoRow label="Usuario" value={session.user.username} />
              <InfoRow label="Sucursal activa" value={activeBranch?.name ?? "Sin seleccionar"} />
            </dl>

            <div className="mt-6 border-t border-[#e4e8df] pt-5">
              <h3 className="app-eyebrow">Cambiar sucursal</h3>
              <div className="mt-3 grid gap-2">
                {branches.map((branch) => (
                  <button
                    className={`rounded-md border px-3 py-3 text-left text-sm transition ${
                      branch.id === session.user.branchId
                        ? "border-[#51735f] bg-[#eef7f0]"
                        : "border-[#d8ddd2] bg-white hover:border-[#51735f]"
                    }`}
                    disabled={switchingBranchId !== null}
                    key={branch.id}
                    onClick={() => switchBranch(branch.id)}
                    type="button"
                  >
                    <span className="block font-black">{branch.name}</span>
                    <span className="mt-1 block text-xs text-[#6f7a73]">
                      {branch.code} - {branch.accessScope} - {branch.status}
                    </span>
                    {switchingBranchId === branch.id ? (
                      <span className="mt-2 block text-xs font-bold text-[#51735f]">Cambiando...</span>
                    ) : null}
                  </button>
                ))}
              </div>
            </div>
          </aside>

          <section className="grid gap-5">
            <section className="app-hero-panel p-6">
              <div className="relative z-10">
                <p className="text-sm font-bold uppercase text-white/75">Turno activo</p>
                <h2 className="mt-2 text-3xl font-black">
                  {activeBranch ? "Lista para operar en mostrador" : "Selecciona una sucursal"}
                </h2>
                <p className="mt-3 max-w-2xl text-sm leading-6 text-white/80">
                  Desde aqui puedes vender, registrar clientes, abrir el kiosco, consultar reportes y cambiar de
                  sucursal sin salir del flujo de recepcion.
                </p>
              </div>
            </section>

            <section className="grid gap-4 md:grid-cols-3">
              {primaryActions.map((item) => (
                <article className="app-card p-5" key={item.title}>
                  <h3 className="app-card-title">{item.title}</h3>
                  <p className="mt-2 app-card-copy">{item.detail}</p>
                  <Link className="mt-5 app-button" href={item.href}>
                    {item.action}
                  </Link>
                </article>
              ))}
            </section>
          </section>
        </section>

        <section className="grid gap-5 lg:grid-cols-[1fr_1fr]">
          <ActionGrid title="Gestion y reportes" items={managementActions} />
          <ActionGrid title="Modulos operativos" items={operationModules} />
        </section>

        <section className="app-card p-5">
          <h2 className="app-card-title">Permisos cargados</h2>
          <PermissionList user={session.user} />
        </section>
      </div>
    </main>
  );
}

function ActionGrid({
  title,
  items,
}: {
  title: string;
  items: Array<{ title: string; detail: string; href: string }>;
}) {
  return (
    <section className="app-card p-5">
      <h2 className="app-card-title">{title}</h2>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        {items.map((item) => (
          <Link className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 transition hover:border-[#51735f]" href={item.href} key={item.href}>
            <span className="block font-black">{item.title}</span>
            <span className="mt-1 block text-sm leading-5 text-[#5d6b63]">{item.detail}</span>
          </Link>
        ))}
      </div>
    </section>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-bold text-[#6f7a73]">{label}</dt>
      <dd className="mt-1 font-black">{value}</dd>
    </div>
  );
}

function PermissionList({ user }: { user: CurrentUser }) {
  if (user.permissions.length === 0) {
    return <p className="mt-3 text-sm text-[#5d6b63]">Sin permisos asignados.</p>;
  }

  return (
    <div className="mt-4 flex flex-wrap gap-2">
      {user.permissions.map((permission) => (
        <span className="app-pill" key={permission}>
          {permission}
        </span>
      ))}
    </div>
  );
}

function Centered({ text }: { text: string }) {
  return (
    <main className="grid min-h-screen place-items-center px-6 text-[#111815]">
      <p className="app-card px-4 py-3 text-sm text-[#42514a]">{text}</p>
    </main>
  );
}
