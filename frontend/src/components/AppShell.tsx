"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { isActive, resolveShellBrand, visibleSectionsForConsole } from "@/components/app-shell-model";
import { AppNotice } from "@/components/AppNotice";
import { clearSession, getBranches, selectBranch, selectOrganization, storeSession, type StoredSession } from "@/lib/api";
import { getConsoleProfile, isPlatformAdmin, isSupportMode, resolveUserConsole } from "@/lib/role-model";

type BranchOption = {
  branchId: string;
  name: string;
};

const BRANCH_RECOVERY_KEY = "gymos:branch-context-recovery";

export function AppShell({
  branchName,
  children,
  eyebrow,
  hideSidebar = false,
  organizationName,
  mode,
  pageClassName,
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
  pageClassName?: string;
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
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
  const [switchingBranch, setSwitchingBranch] = useState(false);
  const [failedBrandLogoUrl, setFailedBrandLogoUrl] = useState<string | null>(null);

  const consoleType = resolveUserConsole(session.user);
  const role = getConsoleProfile(session.user);
  const platformAdmin = isPlatformAdmin(session.user);
  const supportMode = isSupportMode(session.user);
  const hasBranch = mode === "operation" ? true : Boolean(branchName || session.user.branchId);
  const visibleBranch =
    branchName
    ?? (resolvedBranch?.branchId === session.user.branchId ? resolvedBranch.name : null)
    ?? (session.user.branchId ? "Sucursal seleccionada" : "Sin sucursal");
  const permissionSet = new Set(session.user.permissions);
  const showStaticBranchChip = variant !== "platform" && !platformAdmin && consoleType !== "TENANT_OWNER";
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
  const showBrandLogo = Boolean(brand.logoUrl) && failedBrandLogoUrl !== brand.logoUrl;
  const activeNavigationLabel = sections
    .flatMap((section) => section.links)
    .find((item) => isActive(pathname, item.href))?.label ?? "Navegación";

  useEffect(() => {
    if (variant === "platform" || (platformAdmin && !supportMode)) {
      return;
    }
    let cancelled = false;

    async function resolveBranchName() {
      try {
        const branches = await getBranches(session.accessToken);
        if (cancelled) return;
        setAvailableBranches(branches.map((item) => ({ branchId: item.id, name: item.name })));
        if (!branchName && session.user.branchId) {
          const branch = branches.find((item) => item.id === session.user.branchId);
          if (branch) setResolvedBranch({ branchId: branch.id, name: branch.name });
        }
      } catch {
        // The shell keeps a neutral branch label if lookup is unavailable.
      }
    }

    void resolveBranchName();
    return () => {
      cancelled = true;
    };
  }, [branchName, platformAdmin, session.accessToken, session.user.branchId, supportMode, variant]);

  useEffect(() => {
    if (
      hasBranch
      || variant === "platform"
      || platformAdmin
      || consoleType === "TENANT_OWNER"
      || !session.user.organizationId
    ) {
      return;
    }

    const recoveryId = `${session.user.organizationId}:${session.user.userId}`;
    if (window.sessionStorage.getItem(BRANCH_RECOVERY_KEY) === recoveryId) return;

    let cancelled = false;
    window.sessionStorage.setItem(BRANCH_RECOVERY_KEY, recoveryId);

    async function recoverBranchContext() {
      try {
        const nextSession = await selectOrganization(session.accessToken, session.user.organizationId!);
        if (cancelled) return;

        if (nextSession.user.branchId && nextSession.user.branchId !== session.user.branchId) {
          storeSession(nextSession);
          setResolvedBranch({ branchId: nextSession.user.branchId, name: "Sucursal seleccionada" });
          router.replace(pathname);
          router.refresh();
          return;
        }

        window.sessionStorage.removeItem(BRANCH_RECOVERY_KEY);
      } catch {
        window.sessionStorage.removeItem(BRANCH_RECOVERY_KEY);
      }
    }

    void recoverBranchContext();
    return () => {
      cancelled = true;
    };
  }, [consoleType, hasBranch, pathname, platformAdmin, router, session.accessToken, session.user.branchId, session.user.organizationId, session.user.userId, variant]);

  function logout() {
    clearSession();
    window.sessionStorage.removeItem(BRANCH_RECOVERY_KEY);
    router.push("/login");
  }

  async function changeBranch(branchId: string) {
    if (!branchId || branchId === session.user.branchId || switchingBranch) return;
    setSwitchingBranch(true);
    try {
      const selectedSession = await selectBranch(session.accessToken, branchId);
      storeSession(selectedSession);
      window.sessionStorage.removeItem(BRANCH_RECOVERY_KEY);
      // We use hard reload to ensure all contexts, themes and session hooks across the app 
      // are fully synchronized with the new branch identity immediately.
      window.location.reload();
    } finally {
      setSwitchingBranch(false);
    }
  }

  return (
    <main className={`admin-console ${hideSidebar ? "no-sidebar" : ""} ${supportMode ? "support-mode" : ""} ${pageClassName ?? ""}`}>
      <div className="app-notice-region" id="app-notice-region" />
      {!hideSidebar ? (
        <aside className="admin-sidebar">
          <div className="admin-sidebar-brand">
            <span className={showBrandLogo ? "with-logo" : undefined}>
              {showBrandLogo ? (
                <Image alt="" height={48} onError={() => setFailedBrandLogoUrl(brand.logoUrl ?? null)} src={brand.logoUrl!} unoptimized width={48} />
              ) : brand.name.slice(0, 1).toUpperCase()}
            </span>
            <div>
              <strong>{brand.name}</strong>
              <small>{brand.scope}</small>
            </div>
          </div>

          <section className="admin-session-card" aria-label="Sesión activa">
            <div className="admin-session-identity">
              <span>{session.user.fullName}</span>
              <small>{session.user.username}</small>
            </div>
            <button onClick={logout} type="button">Salir</button>
          </section>

          {platformAdmin && !pathname.startsWith("/platform") ? (
            <Link className="admin-platform-return" href="/platform/gymnasiums">
              Volver a administracion de gimnasios
            </Link>
          ) : null}

          <button
            aria-controls="admin-main-navigation"
            aria-expanded={mobileNavigationOpen}
            className="admin-mobile-nav-toggle"
            onClick={() => setMobileNavigationOpen((current) => !current)}
            type="button"
          >
            <span>Menú</span>
            <strong>{activeNavigationLabel}</strong>
            <span aria-hidden="true">{mobileNavigationOpen ? "Cerrar" : "Abrir"}</span>
          </button>

          <nav
            aria-label="Menú principal"
            className={mobileNavigationOpen ? "mobile-open" : undefined}
            id="admin-main-navigation"
          >
            {sections.map((section) => (
              <details className="admin-nav-section" key={section.title} open={sections.length === 1 || section.links.some((item) => isActive(pathname, item.href))}>
                <summary>{section.title}</summary>
                {section.links.map((item) => (
                  <Link
                    aria-current={isActive(pathname, item.href) ? "page" : undefined}
                    className={isActive(pathname, item.href) ? "active" : undefined}
                    href={item.href}
                    key={item.href}
                    onClick={() => setMobileNavigationOpen(false)}
                  >
                    <strong>{item.label}</strong>
                    <span>{item.hint}</span>
                  </Link>
                ))}
              </details>
            ))}
          </nav>

          <div className="admin-sidebar-footer">
            <Link aria-label="Ir al inicio de GymFort" className="admin-product-footer" href={role.homePath}>
              <Image alt="GymFort" height={27} src="/gymfort_web_assets/gymfort-logo-horizontal-white.png" width={122} />
            </Link>
          </div>
        </aside>
      ) : null}

      <section className="admin-workspace">
        {supportMode ? (
          <section className="support-banner" role="status">
            <strong>Modo soporte GymFort</strong>
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
                    <option key={branch.branchId} value={branch.branchId}>{branch.name}</option>
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
            <Link href={platformAdmin ? "/platform/gymnasiums" : "/settings/organization"}>
              {platformAdmin ? "Ir a gimnasios" : "Ir a sucursales"}
            </Link>
          </section>
        ) : null}

        {children}
      </section>
    </main>
  );
}

export function LoadingScreen({ text = "Cargando..." }: { text?: string }) {
  return (
    <main className="min-h-screen bg-[color:var(--brand-surface)] p-6">
      <section className="mx-auto flex min-h-[60vh] max-w-3xl items-center justify-center">
        <div className="app-card w-full max-w-md p-8 text-center" role="status" aria-live="polite">
          <div className="mx-auto mb-4 h-10 w-10 animate-spin rounded-full border-4 border-[#d8ddd2] border-t-[#174f37]" aria-hidden="true" />
          <p className="text-sm font-semibold text-[#435249]">{text}</p>
        </div>
      </section>
    </main>
  );
}

export function InlineError({ message }: { message: string }) {
  return <AppNotice message={message} tone="error" />;
}
