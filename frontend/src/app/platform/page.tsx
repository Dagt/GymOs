"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import { FormError } from "@/components/FormFields";
import { getStoredSession, type StoredSession } from "@/lib/api";
import { getPlatformCustomers } from "@/lib/api/platform-customers";
import type { PlatformCustomerPortfolio } from "@/lib/api/platform-customer-types";
import {
  customerActions,
  customerHealthPresentation,
  lifecycleLabel,
  portfolioCsv,
} from "./platform-portfolio";

export default function PlatformOverviewPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [portfolio, setPortfolio] = useState<PlatformCustomerPortfolio | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    async function load() {
      const stored = getStoredSession();
      if (!stored) {
        if (active) setLoading(false);
        return;
      }
      if (active) setSession(stored);
      try {
        const loaded = await getPlatformCustomers(stored.accessToken);
        if (active) setPortfolio(loaded);
      } catch (loadError) {
        if (active) setError(messageFromError(loadError));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, []);

  const actions = useMemo(
    () => customerActions(portfolio?.customers ?? []),
    [portfolio],
  );

  if (loading) return <LoadingScreen text="Preparando tu cartera de clientes..." />;
  if (!session) {
    return (
      <AuthRequired
        message="Esta consola concentra la relación comercial y operativa de los gimnasios que usan GymFort."
        title="Inicia sesión como administrador de plataforma"
      />
    );
  }

  const summary = portfolio?.summary;
  const customers = portfolio?.customers ?? [];

  function downloadPortfolio() {
    const blob = new Blob(["\uFEFF", portfolioCsv(customers)], {
      type: "text/csv;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `gymfort-cartera-${today()}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <AppShell
      eyebrow="Administración GymFort"
      mode="setup"
      organizationName="GymFort"
      session={session}
      subtitle="Una vista ejecutiva para atender clientes, cuidar renovaciones y mantener la cobranza bajo control."
      title="Cartera de gimnasios"
      variant="platform"
    >
      {error ? <FormError message={error} /> : null}

      <section className="platform-toolbar">
        <div>
          <p className="app-eyebrow">Hoy</p>
          <h2>Lo que necesita tu atención</h2>
          <span>Prioriza vencidos, implementaciones y seguimientos sin mezclar la operación de cada tenant.</span>
        </div>
        <div>
          <button className="app-button-secondary" disabled={customers.length === 0} onClick={downloadPortfolio} type="button">
            Exportar cartera CSV
          </button>
        </div>
      </section>

      <section className="platform-kpi-grid" aria-label="Indicadores de cartera">
        <Metric label="MRR configurado" value={formatCurrency(summary?.monthlyRecurringRevenue ?? 0)} />
        <Metric label="Clientes" value={formatNumber(summary?.customerCount ?? 0)} />
        <Metric label="En implementación" value={formatNumber(summary?.onboardingCustomerCount ?? 0)} />
        <Metric label="En riesgo" value={formatNumber(summary?.atRiskCustomerCount ?? 0)} tone={(summary?.atRiskCustomerCount ?? 0) > 0 ? "danger" : "neutral"} />
        <Metric label="Sucursales vencidas" value={formatNumber(summary?.overdueBranchCount ?? 0)} tone={(summary?.overdueBranchCount ?? 0) > 0 ? "danger" : "neutral"} />
        <Metric label="Seguimientos próximos" value={formatNumber(summary?.followUpsDueCount ?? 0)} tone={(summary?.followUpsDueCount ?? 0) > 0 ? "warning" : "neutral"} />
      </section>

      <section className="admin-panel platform-action-center">
        <header className="admin-panel-header">
          <div>
            <p>Centro de acción</p>
            <h2>{actions.length ? `${actions.length} asuntos priorizados` : "Cartera sin pendientes críticos"}</h2>
            <span>Las alertas se derivan del expediente, la configuración y el estado real de cobro.</span>
          </div>
        </header>
        <div className="platform-action-list">
          {actions.slice(0, 8).map((action, index) => (
            <Link href={action.destination} key={`${action.organizationId}:${action.title}`}>
              <span className={`platform-action-priority priority-${action.priority}`}>{index + 1}</span>
              <span>
                <small>{action.customerName}</small>
                <strong>{action.title}</strong>
                <em>{action.detail}</em>
              </span>
              <b aria-hidden="true">→</b>
            </Link>
          ))}
          {!error && actions.length === 0 ? (
            <div className="admin-empty-card">
              <strong>Todo bajo control</strong>
              <span>No hay vencidos, seguimientos atrasados ni altas comerciales incompletas.</span>
            </div>
          ) : null}
        </div>
      </section>

      <section className="admin-panel">
        <header className="admin-panel-header">
          <div>
            <p>Portafolio</p>
            <h2>Salud de tus clientes</h2>
            <span>Un resumen comercial por Organization; la operación y facturación siguen desglosadas por sucursal.</span>
          </div>
          <Link className="admin-text-button" href="/platform/gymnasiums">Ver todos</Link>
        </header>
        <div className="platform-customer-cards">
          {customers.slice(0, 8).map((customer) => {
            const health = customerHealthPresentation(customer.healthStatus);
            return (
              <article key={customer.organizationId}>
                <div>
                  <span className={`platform-status-badge ${health.tone}`}>{health.label}</span>
                  <small>{lifecycleLabel(customer.lifecycleStatus)}</small>
                </div>
                <h3>{customer.tradeName}</h3>
                <p>{customer.activeBranchCount} {customer.activeBranchCount === 1 ? "sucursal activa" : "sucursales activas"} · {formatNumber(customer.activeMembers)} socios</p>
                <dl>
                  <div><dt>MRR</dt><dd>{formatCurrency(customer.monthlyRecurringRevenue)}</dd></div>
                  <div><dt>Responsable</dt><dd>{customer.accountOwner || "Por asignar"}</dd></div>
                  <div><dt>Próximo cobro</dt><dd>{customer.nextBillingDate ? formatDate(customer.nextBillingDate) : "Sin fecha"}</dd></div>
                </dl>
              </article>
            );
          })}
          {!error && customers.length === 0 ? <div className="admin-empty-card">Crea tu primer gimnasio cliente para iniciar la cartera.</div> : null}
        </div>
      </section>
    </AppShell>
  );
}

function Metric({ label, value, tone = "neutral" }: { label: string; value: string; tone?: "neutral" | "warning" | "danger" }) {
  return <article className={`platform-kpi ${tone}`}><span>{label}</span><strong>{value}</strong></article>;
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN", maximumFractionDigits: 0 }).format(value);
}

function formatNumber(value: number) {
  return new Intl.NumberFormat("es-MX").format(value);
}

function formatDate(value: string) {
  const [year, month, day] = value.split("-");
  return year && month && day ? `${day}/${month}/${year}` : value;
}

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function messageFromError(error: unknown) {
  return error instanceof Error ? error.message : "No fue posible cargar la cartera de clientes.";
}
