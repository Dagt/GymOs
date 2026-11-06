"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import { getVisibleFocusActions } from "./reception-model";
import {
  getBranches,
  getCurrentOrganization,
  getStoredSession,
  selectBranch,
  type BranchSummary,
  type OrganizationSummary,
  type StoredSession,
} from "@/lib/api";
import { ReceptionRenewalWidget } from "./ReceptionRenewalWidget";
import { canLaunchKiosk, canRegisterCheckIn } from "@/lib/role-model";

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
        let effectiveSession: StoredSession = storedSession;
        const onlyBranch = availableBranches.length === 1 ? availableBranches[0] : null;
        if (!storedSession.user.branchId && onlyBranch) {
          const nextSession = await selectBranch(storedSession.accessToken, onlyBranch.id);
          effectiveSession = {
            accessToken: nextSession.accessToken,
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
    organization?.tradeName ?? session?.user.brandName ?? session?.user.organizationName ?? "GymFort";
  const focusActions = session ? getVisibleFocusActions(canLaunchKiosk(session.user), canRegisterCheckIn(session.user)) : [];

  async function switchBranch(branchId: string) {
    if (!session || branchId === session.user.branchId) {
      return;
    }

    setSwitchingBranchId(branchId);
    setError(null);
    try {
      const nextSession = await selectBranch(session.accessToken, branchId);
      setSession({
        accessToken: nextSession.accessToken,
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
      branchName={visibleBranchName}
      eyebrow="Recepcion"
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
        <div className="flex flex-col gap-8 w-full max-w-7xl mx-auto">
          <ReceptionRenewalWidget session={session} />

          {/* Main Hub Grid */}
          <section>
            <h3 className="text-lg font-black text-[color:var(--brand-primary)] mb-5 px-1 uppercase tracking-widest opacity-60">Módulos Operativos</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {focusActions.map((action, index) => {
                const isPrimary = index === 0;
                return (
                  <Link
                    key={action.href}
                    href={action.href}
                    className={`group relative flex flex-col p-6 rounded-2xl border-2 transition-all duration-300 hover:-translate-y-1 hover:shadow-xl overflow-hidden ${
                      isPrimary 
                        ? "col-span-1 md:col-span-2 lg:col-span-2 shadow-lg" 
                        : "shadow-sm bg-white"
                    }`}
                    style={isPrimary ? {
                      background: `var(--brand-primary)`,
                      borderColor: `color-mix(in srgb, var(--brand-primary) 80%, black)`,
                      color: 'white'
                    } : {
                      background: `white`,
                      borderColor: `var(--brand-line)`
                    }}
                  >
                    {/* Decorative Background Blob */}
                    <div 
                      className={`absolute -top-24 -right-24 w-64 h-64 rounded-full blur-3xl transition-opacity duration-500 ${isPrimary ? 'opacity-20 group-hover:opacity-40' : 'opacity-[0.05] group-hover:opacity-10'}`}
                      style={{ background: isPrimary ? 'white' : 'var(--brand-primary)' }}
                    ></div>

                    <div className="relative z-10 flex items-center justify-between mb-5">
                      <span className={`text-xs font-black uppercase tracking-widest px-4 py-2 rounded-full shadow-sm ${
                        isPrimary 
                          ? "bg-amber-400 text-slate-950 font-black" 
                          : "bg-[color:var(--brand-surface)] text-[color:var(--brand-primary)] border border-[color:var(--brand-line)]"
                      }`}>
                        {action.title}
                      </span>
                    </div>
                    
                    <h3 className={`text-2xl font-black mb-3 relative z-10 drop-shadow-sm ${
                      isPrimary ? "text-white" : "text-[color:var(--brand-ink)] group-hover:text-[color:var(--brand-primary)] transition-colors"
                    }`}>
                      {action.label}
                    </h3>
                    
                    <p className={`text-base font-medium flex-grow relative z-10 ${
                      isPrimary ? "text-white/90" : "text-[color:var(--brand-muted)]"
                    }`}>
                      {action.detail}
                    </p>

                    {isPrimary && (
                      <div className="relative z-10 mt-6 inline-flex self-start items-center justify-center px-6 py-3 bg-white text-[color:var(--brand-primary)] text-sm font-black rounded-xl hover:bg-gray-50 transition-colors shadow-sm">
                        Abrir módulo →
                      </div>
                    )}
                  </Link>
                );
              })}
            </div>
          </section>
        </div>
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
