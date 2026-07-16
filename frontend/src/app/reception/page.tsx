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

const shortcuts = [
  {
    title: "POS",
    detail: "Ventas con pagos manuales y validacion interna.",
    status: "Disponible",
    href: "/pos",
  },
  {
    title: "Alta rapida",
    detail: "Registro de cliente y captura de datos base.",
    status: "Disponible",
    href: "/clients",
  },
  {
    title: "Catalogo",
    detail: "Productos, servicios y planes vendibles.",
    status: "Disponible",
    href: "/catalog",
  },
  {
    title: "Reportes",
    detail: "Resumen de ventas, saldos y pagos manuales.",
    status: "Disponible",
    href: "/reports/sales",
  },
  {
    title: "Configuracion",
    detail: "Datos de organizacion y sucursal para el arranque.",
    status: "Disponible",
    href: "/settings/organization",
  },
  {
    title: "Onboarding",
    detail: "Checklist y setup piloto para dejar el gimnasio vendible.",
    status: "Disponible",
    href: "/onboarding",
  },
  {
    title: "Importar clientes",
    detail: "Carga inicial CSV con validacion y resultado por fila.",
    status: "Disponible",
    href: "/clients/import",
  },
  {
    title: "Kiosco interno",
    detail: "Autoservicio para pagos, renovaciones y cambios de membresia.",
    status: "Disponible",
    href: "/kiosk/KIOSK-MAIN",
  },
  {
    title: "Accesos",
    detail: "Entrada, aperturas manuales, pases temporales y bitacora.",
    status: "Disponible",
    href: "/access",
  },
  {
    title: "Inventario",
    detail: "Existencias, movimientos, ajustes, mermas y alertas.",
    status: "Disponible",
    href: "/inventory",
  },
  {
    title: "Reservas",
    detail: "Recursos, disponibilidad, agenda y check-in.",
    status: "Disponible",
    href: "/bookings",
  },
  {
    title: "Coaches",
    detail: "Entrenadores, rutinas y asignaciones a clientes.",
    status: "Disponible",
    href: "/coaches",
  },
  {
    title: "Bienestar",
    detail: "Profesionales, servicios y disponibilidad.",
    status: "Disponible",
    href: "/wellness",
  },
  {
    title: "Automatizaciones",
    detail: "Reglas, ejecuciones y notificaciones internas.",
    status: "Disponible",
    href: "/automations",
  },
  {
    title: "Dashboard",
    detail: "Indicadores ejecutivos de operacion y venta.",
    status: "Disponible",
    href: "/dashboard",
  },
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
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">
          Cargando recepcion...
        </p>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
          <p className="text-sm font-semibold uppercase tracking-wide text-[#51735f]">GymOS</p>
          <h1 className="mt-3 text-2xl font-semibold">Necesitas iniciar sesion</h1>
          <p className="mt-3 text-sm leading-6 text-[#5d6b63]">
            La recepcion requiere una sesion activa para cargar organizacion,
            permisos y sucursales disponibles.
          </p>
          <Link
            className="mt-5 inline-flex h-11 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
            href="/login"
          >
            Iniciar sesion
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-5 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-[#51735f]">Recepcion</p>
            <h1 className="mt-1 text-3xl font-semibold">
              {organization?.tradeName ?? "GymOS"}
            </h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              {activeBranch ? activeBranch.name : "Selecciona una sucursal para operar"}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <span className="rounded-md border border-[#d8ddd2] px-3 py-2 text-sm text-[#42514a]">
              {session.user.fullName}
            </span>
            <button
              className="h-10 rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
              onClick={logout}
              type="button"
            >
              Salir
            </button>
          </div>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[1.1fr_1.4fr]">
        <aside className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Contexto operativo</h2>
          <dl className="mt-5 grid gap-4 text-sm">
            <div>
              <dt className="font-medium text-[#6f7a73]">Organizacion</dt>
              <dd className="mt-1 font-semibold">{organization?.legalName ?? "Sin cargar"}</dd>
            </div>
            <div>
              <dt className="font-medium text-[#6f7a73]">Moneda</dt>
              <dd className="mt-1 font-semibold">{organization?.defaultCurrency ?? "N/A"}</dd>
            </div>
            <div>
              <dt className="font-medium text-[#6f7a73]">Usuario</dt>
              <dd className="mt-1 font-semibold">{session.user.username}</dd>
            </div>
          </dl>

          <div className="mt-6 border-t border-[#e4e8df] pt-5">
            <h3 className="text-sm font-semibold uppercase tracking-wide text-[#51735f]">
              Sucursales disponibles
            </h3>
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
                  <span className="block font-semibold">{branch.name}</span>
                  <span className="mt-1 block text-xs text-[#6f7a73]">
                    {branch.code} - {branch.accessScope} - {branch.status}
                  </span>
                  {switchingBranchId === branch.id ? (
                    <span className="mt-2 block text-xs font-semibold text-[#51735f]">
                      Cambiando...
                    </span>
                  ) : null}
                </button>
              ))}
            </div>
          </div>
        </aside>

        <section className="grid gap-6">
          {error ? (
            <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
              {error}
            </p>
          ) : null}

          <div className="rounded-lg border border-[#d8ddd2] bg-[#13251c] p-5 text-white shadow-sm">
            <p className="text-sm font-medium text-[#9cc7ad]">Sesion activa</p>
            <h2 className="mt-2 text-2xl font-semibold">
              {activeBranch ? "Lista para operar en sucursal" : "Falta seleccionar sucursal"}
            </h2>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-[#dbe8df]">
              El token actual contiene organizacion, usuario, permisos y sucursal activa.
              Este contexto sera usado por clientes, ventas, pagos y kiosco.
            </p>
          </div>

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {shortcuts.map((shortcut) => (
              <article className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" key={shortcut.title}>
                <p className="text-xs font-semibold uppercase tracking-wide text-[#51735f]">
                  {shortcut.status}
                </p>
                <h3 className="mt-3 text-xl font-semibold">{shortcut.title}</h3>
                <p className="mt-3 text-sm leading-6 text-[#5d6b63]">{shortcut.detail}</p>
                {shortcut.href ? (
                  <Link
                    className="mt-4 inline-flex h-10 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
                    href={shortcut.href}
                  >
                    Abrir
                  </Link>
                ) : null}
              </article>
            ))}
          </div>

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Permisos cargados</h2>
            <PermissionList user={session.user} />
          </section>
        </section>
      </section>
    </main>
  );
}

function PermissionList({ user }: { user: CurrentUser }) {
  if (user.permissions.length === 0) {
    return <p className="mt-3 text-sm text-[#5d6b63]">Sin permisos asignados.</p>;
  }

  return (
    <div className="mt-4 flex flex-wrap gap-2">
      {user.permissions.map((permission) => (
        <span
          className="rounded-md border border-[#d8ddd2] bg-[#f6f7f2] px-3 py-2 text-xs font-medium text-[#42514a]"
          key={permission}
        >
          {permission}
        </span>
      ))}
    </div>
  );
}
