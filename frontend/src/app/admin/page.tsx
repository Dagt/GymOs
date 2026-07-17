"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
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

export default function AdminPage() {
  const router = useRouter();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectingBranchId, setSelectingBranchId] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!stored) {
        setLoading(false);
        return;
      }

      setSession(stored);
      try {
        const [currentOrganization, availableBranches] = await Promise.all([
          getCurrentOrganization(stored.accessToken),
          getBranches(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(currentOrganization);
        setBranches(availableBranches);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar administración.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => {
      active = false;
    };
  }, []);

  async function activateBranch(branchId: string) {
    if (!session) {
      return;
    }

    setSelectingBranchId(branchId);
    setError(null);
    try {
      const nextSession = await selectBranch(session.accessToken, branchId);
      storeSession(nextSession);
      setSession(nextSession);
      router.push("/dashboard");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible seleccionar sucursal.");
    } finally {
      setSelectingBranchId(null);
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando administración..." />;
  }

  if (!session) {
    return (
      <AuthRequired
        message="El panel de administración permite elegir gimnasio, preparar sucursales y entrar al contexto operativo."
        title="Inicia sesión como administrador"
      />
    );
  }

  const activeBranch = branches.find((branch) => branch.id === session.user.branchId) ?? null;

  return (
    <AppShell
      actions={[
        { href: "/settings/organization", label: "Datos y sucursales" },
        { href: "/onboarding", label: "Preparar piloto" },
      ]}
      branchName={activeBranch?.name}
      eyebrow="Administrador GymOS"
      mode={activeBranch ? "operation" : "setup"}
      organizationName={organization?.tradeName}
      session={session}
      subtitle="Elige el gimnasio y la sucursal sobre la que vas a trabajar. Después entrarás a reportes, caja y operación."
      title="Gimnasios"
    >
      {error ? <InlineError message={error} /> : null}

      <section className="admin-home-grid">
        <section className="admin-home-main">
          <section className="admin-hero-card">
            <div>
              <p>Gimnasio actual</p>
              <h2>{organization?.tradeName ?? "Gimnasio sin nombre"}</h2>
              <span>{organization?.legalName ?? "Completa los datos comerciales para identificar este gimnasio."}</span>
            </div>
            <Link href="/settings/organization">Editar datos y sucursales</Link>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Sucursales</p>
                <h2>Selecciona contexto operativo</h2>
              </div>
              <Link href="/settings/organization">Administrar sucursales</Link>
            </div>

            <div className="admin-branch-cards">
              {branches.length === 0 ? (
                <section className="admin-empty-card">
                  <strong>Aún no hay sucursales</strong>
                  <span>Crea la primera sucursal para poder operar caja, socios, membresías y reportes.</span>
                  <Link href="/settings/organization">Crear sucursal</Link>
                </section>
              ) : null}

              {branches.map((branch) => (
                <article className={branch.id === session.user.branchId ? "active" : undefined} key={branch.id}>
                  <div>
                    <p>{branch.code}</p>
                    <h3>{branch.name}</h3>
                    <span>{branch.phone ?? "Sin teléfono registrado"}</span>
                  </div>
                  <button
                    disabled={selectingBranchId !== null || branch.id === session.user.branchId}
                    onClick={() => activateBranch(branch.id)}
                    type="button"
                  >
                    {branch.id === session.user.branchId ? "Sucursal activa" : "Usar esta sucursal"}
                  </button>
                </article>
              ))}
            </div>
          </section>
        </section>

        <aside className="admin-home-side">
          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Siguiente</p>
                <h2>Ruta recomendada</h2>
              </div>
            </div>
            <ol className="admin-route-list">
              <li>
                <strong>1. Revisa el gimnasio</strong>
                <span>Nombre comercial, contacto, moneda y zona horaria.</span>
              </li>
              <li>
                <strong>2. Selecciona sucursal</strong>
                <span>Esto activa el contexto para reportes y operación diaria.</span>
              </li>
              <li>
                <strong>3. Entrega al dueño</strong>
                <span>Catálogo, coaches e inventario pueden completarse después.</span>
              </li>
            </ol>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Alta rápida</p>
                <h2>Preparar piloto</h2>
              </div>
            </div>
            <p className="admin-muted-text">
              Usa este flujo sólo cuando quieras generar datos mínimos de demostración para un gimnasio real.
            </p>
            <Link className="admin-secondary-link" href="/onboarding">
              Abrir preparación guiada
            </Link>
          </section>
        </aside>
      </section>
    </AppShell>
  );
}
