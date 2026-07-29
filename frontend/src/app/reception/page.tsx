"use client";

import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import { DAY_SIGNALS, FOCUS_ACTIONS } from "./reception-model";
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
        <div className="flex flex-col gap-6 w-full max-w-7xl mx-auto">
          {/* Identity & Shift Panel */}
          <section 
            className="relative flex flex-col md:flex-row justify-between items-start md:items-center gap-6 p-8 rounded-2xl shadow-xl overflow-hidden text-white"
            style={{ 
              background: `linear-gradient(135deg, color-mix(in srgb, var(--brand-primary) 90%, black), color-mix(in srgb, var(--brand-primary) 60%, var(--brand-accent)))`,
            }}
          >
            {/* Texture Overlay */}
            <div className="absolute inset-0 opacity-10 pointer-events-none" style={{ backgroundImage: `repeating-linear-gradient(45deg, transparent, transparent 10px, rgba(255,255,255,0.2) 10px, rgba(255,255,255,0.2) 20px)`}}></div>
            
            <div className="relative z-10 flex items-center gap-5">
              {session.user.logoUrl ? (
                <Image alt={session.user.brandName ?? receptionOrganizationName} className="rounded-xl border-2 border-white/30 shadow-md bg-white/10 p-1" height={80} src={session.user.logoUrl} unoptimized width={80} />
              ) : (
                <div className="h-20 w-20 bg-white/20 border-2 border-white/40 text-white rounded-xl flex items-center justify-center text-3xl font-black shadow-md" aria-hidden="true">
                  {(session.user.brandName ?? receptionOrganizationName).slice(0, 1)}
                </div>
              )}
              <div>
                <p className="text-sm font-semibold uppercase tracking-widest opacity-80">{receptionOrganizationName}</p>
                <h2 className="text-3xl font-black leading-tight drop-shadow-sm">{visibleBranchName}</h2>
                <span className="text-sm font-medium mt-1 block opacity-90">
                  Operador: {session.user.fullName}
                </span>
              </div>
            </div>
            
            <div className="relative z-10 flex flex-wrap gap-3 self-stretch md:self-auto">
              {DAY_SIGNALS.map((signal) => (
                <div key={signal.label} className="flex flex-col justify-center bg-white/10 backdrop-blur-md rounded-xl px-4 py-3 border border-white/20 min-w-[120px] shadow-sm">
                  <span className="text-[0.65rem] font-bold uppercase tracking-wider opacity-70 mb-1">{signal.label}</span>
                  <strong className="text-lg font-black leading-none drop-shadow-sm">{signal.value}</strong>
                </div>
              ))}
              <button 
                onClick={logout} 
                type="button"
                className="flex flex-col justify-center items-center bg-black/20 hover:bg-black/40 transition-colors backdrop-blur-md rounded-xl px-5 py-3 border border-white/10 shadow-sm"
              >
                <strong className="text-sm font-bold">Salir</strong>
              </button>
            </div>
          </section>

          {/* Unified Hub Grid */}
          <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {FOCUS_ACTIONS.map((action, index) => {
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
                    background: `color-mix(in srgb, var(--brand-primary) 2%, white)`,
                    borderColor: `color-mix(in srgb, var(--brand-primary) 15%, transparent)`,
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
                        ? "bg-white/20 text-white backdrop-blur-md border border-white/20" 
                        : "bg-white text-[color:var(--brand-primary)] border border-[color:color-mix(in_srgb,var(--brand-primary)_20%,transparent)]"
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
                    <div className="relative z-10 mt-6 inline-flex self-start items-center justify-center px-8 py-3 bg-[color:var(--brand-accent)] text-white text-lg font-black rounded-xl hover:scale-105 transition-transform shadow-md">
                      Abrir módulo →
                    </div>
                  )}
                </Link>
              );
            })}
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

function ReportMetric({ label, value, tone }: { label: string; value: string; tone: string }) {
  return (
    <article className={`admin-report-metric ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}
