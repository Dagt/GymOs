"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { isActive, resolveShellBrand, visibleSectionsForConsole } from "@/components/app-shell-model";
import { clearSession, getBranches, selectBranch, selectOrganization, storeSession, type StoredSession } from "@/lib/api";
import { getConsoleProfile, isPlatformAdmin, isSupportMode, resolveUserConsole } from "@/lib/role-model";

type BranchOption = {
  branchId: string;
  name: string;
};

export function AppShell({
  branchName,
  children,
  eyebrow,
  hideSidebar = false,
  organizationName,
  mode,
  session,
  subtitle,
  title,
  variant,
}: {
  branchName?: string | null;
  children: ReactNode;
  eyebrow: string;
  hideSidebar?: boolean;
  mode?: "setup" | "operation";
  organizationName?: string | null;
  session: StoredSession;
  subtitle?: string;
  title: string;
  variant?: "platform" | "tenant";
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [resolvedBranch, setResolvedBranch] = useState<BranchOption | null>(null);
  const [availableBranches, setAvailableBranches] = useState<BranchOption[]>([]);
  const [switchingBranch, setSwitchingBranch] = useState(false);

  const consoleType = resolveUserConsole(session.user);
  const role = getConsoleProfile(session.user);
  const hasBranch = mode === "operation" ? true : Boolean(branchName || session.user.branchId);
  const visibleBranch =
    branchName
    ?? (resolvedBranch?.branchId === session.user.branchId ? resolvedBranch.name : null)
    ?? (session.user.branchId ? "Sucursal seleccionada" : "Sin sucursal");
  const permissionSet = new Set(session.user.permissions);
  const showStaticBranchChip = variant !== "platform" && !isPlatformAdmin(session.user) && consoleType !== "TENANT_OWNER";
  const isSingleBranchOperationalUser =
    (consoleType === "TENANT_MANAGER" || consoleType === "RECEPTION" || consoleType === "COACH")
    && availableBranches.length <= 1;
  const showBranchSwitcher = variant !== "platform" && availableBranches.length > 0 && !isSingleBranchOperationalUser;
  const sections = visibleSectionsForConsole({
    consoleType,
    hasBranch,
    permissions: permissionSet,
    variant,
  });
  const brand = resolveShellBrand({
    organizationName,
    session,
    variant,
    visibleBranch,
  });

  useEffect(() => {
    if (variant === "platform" || (isPlatformAdmin(session.user) && !isSupportMode(session.user))) {
      return;
    }
    let cancelled = false;

    async function resolveBranchName() {
      try {
        const branches = await getBranches(session.accessToken);
        if (cancelled) {
          return;
        }
        setAvailableBranches(branches.map((item) => ({ branchId: item.id, name: item.name })));
        if (!branchName && session.user.branchId) {
          const branch = branches.find((item) => item.id === session.user.branchId);
          if (branch) {
            setResolvedBranch({ branchId: branch.id, name: branch.name });
          }
        }
      } catch {
        // The shell keeps a neutral branch label if lookup is unavailable.
      }
    }

    void resolveBranchName();
    return () => {
      cancelled = true;
    };
  }, [branchName, session.accessToken, session.user, session.user.branchId, variant]);

  useEffect(() => {
    if (hasBranch || variant === "platform" || isPlatformAdmin(session.user) || consoleType === "TENANT_OWNER" || !session.user.organizationId) {
      return;
    }
    let cancelled = false;

    async function recoverBranchContext() {
      try {
        const nextSession = await selectOrganization(session.accessToken, session.user.organizationId);
        if (!cancelled && nextSession.user.branchId) {
          storeSession(nextSession);
          window.location.reload();
        }
      } catch {
        // The setup banner remains visible when the user truly has no branch access.
      }
    }

    void recoverBranchContext();
    return () => {
      cancelled = true;
    };
  }, [consoleType, hasBranch, session, variant]);

  function logout() {
    clearSession();
    router.push("/login");
  }

  async function changeBranch(branchId: string) {
    if (!branchId || branchId === session.user.branchId || switchingBranch) {
      return;
    }
    setSwitchingBranch(true);
    try {
      const selectedSession = await selectBranch(session.accessToken, branchId);
      storeSession(selectedSession);
      window.location.reload();
    } finally {
      setSwitchingBranch(false);
    }
  }

  return (
    <main className={`admin-console ${hideSidebar ? "no-sidebar" : ""} ${isSupportMode(session.user) ? "support-mode" : ""}`}>
      {!hideSidebar ? (
        <aside className="admin-sidebar">
          <div className="admin-sidebar-brand">
            <span className={brand.logoUrl ? "with-logo" : undefined}>
              {brand.logoUrl ? <Image alt="" height={48} src={brand.logoUrl} unoptimized width={48} /> : "G"}
            </span>
            <div>
              <strong>{brand.name}</strong>
              <small>{brand.scope}</small>
            </div>
          </div>

          <section className="admin-user-card" aria-label="Perfil activo">
            <p>Entraste como</p>
            <strong>{role.label}</strong>
            <span>{role.description}</span>
          </section>

          {isPlatformAdmin(session.user) && !pathname.startsWith("/platform") ? (
            <Link className="admin-platform-return" href="/platform/gymnasiums">
              Volver a administracion de gimnasios
            </Link>
          ) : null}

          <nav aria-label="Menu principal">
            {sections.map((section) => (
              <details className="admin-nav-section" key={section.title} open={sections.length === 1 || section.links.some((item) => isActive(pathname, item.href))}>
                <summary>{section.title}</summary>
                {section.links.map((item) => (
                  <Link
                    aria-current={isActive(pathname, item.href) ? "page" : undefined}
                    className={isActive(pathname, item.href) ? "active" : undefined}
                    href={item.href}
                    key={item.href}
                  >
                    <strong>{item.label}</strong>
                    <span>{item.hint}</span>
                  </Link>
                ))}
              </details>
            ))}
          </nav>

          <div className="admin-sidebar-footer">
            <span>{session.user.fullName}</span>
            <small>{session.user.username}</small>
            <button onClick={logout} type="button">
              Salir
            </button>
          </div>
        </aside>
      ) : null}

      <section className="admin-workspace">
        {isSupportMode(session.user) ? (
          <section className="support-banner" role="status">
            <strong>Modo soporte GymOS</strong>
            <span>Estas operando dentro de este gimnasio para apoyar al cliente. Las acciones quedan auditadas.</span>
          </section>
        ) : null}

        <header className="admin-command-bar">
          <div>
            <p>{eyebrow}</p>
            <h1>{title}</h1>
            {subtitle ? <span>{subtitle}</span> : null}
          </div>
          <div className="admin-command-actions">
            <div className="admin-role-chip">
              <span>Perfil</span>
              <strong>{role.label}</strong>
            </div>
            {showStaticBranchChip ? (
              <div className="admin-role-chip">
                <span>Sucursal</span>
                <strong>{visibleBranch}</strong>
              </div>
            ) : null}
            {showBranchSwitcher ? (
              <label className="admin-branch-switcher">
                <span>Operar en</span>
                <select disabled={switchingBranch} onChange={(event) => changeBranch(event.target.value)} value={session.user.branchId ?? ""}>
                  <option value="">Sin sucursal</option>
                  {availableBranches.map((branch) => (
                    <option key={branch.branchId} value={branch.branchId}>
                      {branch.name}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
          </div>
        </header>

        {!hasBranch && variant !== "platform" ? (
          <section className="admin-setup-banner">
            <div>
              <p>Configuracion pendiente</p>
              <h2>Primero selecciona una sucursal operativa</h2>
              <span>
                Caja, recepcion, membresias, reservas y kiosco necesitan una sucursal activa para operar sin mezclar sedes.
              </span>
            </div>
            <Link href={isPlatformAdmin(session.user) ? "/platform/gymnasiums" : "/settings/organization"}>
              {isPlatformAdmin(session.user) ? "Ir a gimnasios" : "Ir a sucursales"}
            </Link>
          </section>
        ) : null}

        {children}
      </section>
    </main>
  );
}

export function LoadingScreen({ text }: { text: string }) {
  return (
    <main className="admin-empty-state">
      <section>
        <p>GymOS</p>
        <h1>{text}</h1>
      </section>
    </main>
  );
}

export function InlineError({ message }: { message: string }) {
  return (
    <section className="admin-alert" role="alert">
      <strong>No se pudo cargar esta seccion</strong>
      <span>{message}</span>
    </section>
  );
}
