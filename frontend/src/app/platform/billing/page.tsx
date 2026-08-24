"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppNotice } from "@/components/AppNotice";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import { DateField, FormError, NumberField, SelectField, TextAreaField, TextField } from "@/components/FormFields";
import { getStoredSession, type StoredSession } from "@/lib/api";
import { getPlatformBillingOverview, getPlatformBillingPayments, recordPlatformBillingPayment, updatePlatformBranchBilling } from "@/lib/api/platform-billing";
import type { PlatformBillingOverview, PlatformBillingPayment, PlatformBillingPaymentPayload, PlatformBillingUpdatePayload, PlatformBranchBilling } from "@/lib/api/platform-billing-types";

const planOptions = [
  { value: "GYMFORT_150", label: "GymFort 150 · $1,190" },
  { value: "GYMFORT_500", label: "GymFort 500 · $1,590" },
  { value: "GYMFORT_1000", label: "GymFort 1000 · $2,190" },
  { value: "GYMFORT_2000", label: "GymFort 2000 · $2,990" },
  { value: "ENTERPRISE", label: "GymFort Enterprise" },
];

const subscriptionOptions = [
  { value: "ACTIVE", label: "Activa" },
  { value: "PENDING", label: "Pendiente" },
  { value: "PAUSED", label: "Pausada" },
  { value: "CANCELLED", label: "Cancelada" },
];

const paymentTypeOptions = [
  { value: "SUBSCRIPTION", label: "Mensualidad GymFort" },
  { value: "IMPLEMENTATION", label: "Implementación" },
  { value: "OTHER", label: "Otro concepto" },
];

const paymentMethodOptions = [
  { value: "TRANSFER", label: "Transferencia" },
  { value: "CASH", label: "Efectivo" },
  { value: "CARD", label: "Tarjeta" },
  { value: "STRIPE", label: "Stripe" },
  { value: "OTHER", label: "Otro" },
];

export default function PlatformBillingPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [overview, setOverview] = useState<PlatformBillingOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [query, setQuery] = useState("");
  const [paymentFilter, setPaymentFilter] = useState("ALL");
  const [selected, setSelected] = useState<PlatformBranchBilling | null>(null);
  const [form, setForm] = useState<PlatformBillingUpdatePayload | null>(null);
  const [payments, setPayments] = useState<PlatformBillingPayment[]>([]);
  const [paymentForm, setPaymentForm] = useState<PlatformBillingPaymentPayload | null>(null);
  const [paymentError, setPaymentError] = useState("");
  const [recordingPayment, setRecordingPayment] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      const stored = getStoredSession();
      if (!stored) {
        if (!cancelled) setLoading(false);
        return;
      }
      if (!cancelled) setSession(stored);
      try {
        const next = await getPlatformBillingOverview(stored.accessToken);
        if (!cancelled) {
          setOverview(next);
          const requestedOrganizationId = new URLSearchParams(window.location.search).get("organization");
          const requested = next.branches.find((branch) => branch.organizationId === requestedOrganizationId);
          if (requested) setQuery(requested.organizationName);
        }
      } catch (loadError) {
        if (!cancelled) setError(messageFromError(loadError));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, []);

  const customerRows = useMemo(() => groupByCustomer(overview?.branches ?? []), [overview]);
  const filteredBranches = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return [...(overview?.branches ?? [])]
      .filter((branch) => {
        const matchesQuery = !normalizedQuery || [branch.organizationName, branch.branchName, branch.branchCode]
          .some((value) => value.toLowerCase().includes(normalizedQuery));
        return matchesQuery && (paymentFilter === "ALL" || branch.paymentStatus === paymentFilter);
      })
      .sort(compareBillingUrgency);
  }, [overview, paymentFilter, query]);

  if (loading) return <LoadingScreen text="Cargando cobros y contratos..." />;
  if (!session) return <AuthRequired message="Este panel es exclusivo de la administración comercial de GymFort." title="Inicia sesión como administrador de plataforma" />;

  const summary = overview?.summary;

  async function openBilling(branch: PlatformBranchBilling) {
    setSelected(branch);
    setError("");
    setMessage("");
    setForm(toForm(branch));
    setPaymentError("");
    setPayments([]);
    setPaymentForm(toPaymentForm(branch));
    if (!session) return;
    try {
      setPayments(await getPlatformBillingPayments(session.accessToken, branch.branchId));
    } catch (historyError) {
      setPaymentError(messageFromError(historyError));
    }
  }

  function closeBilling() {
    if (saving || recordingPayment) return;
    setSelected(null);
    setForm(null);
    setPayments([]);
    setPaymentForm(null);
    setPaymentError("");
    setError("");
    setMessage("");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form || !selected || !session) return;
    if (form.billingAnchorDay < 1 || form.billingAnchorDay > 31) {
      setError("El dia de corte debe estar entre 1 y 31.");
      return;
    }
    if (form.contractedPlanCode === "ENTERPRISE" && form.monthlyPriceOverride == null) {
      setError("Captura el precio mensual acordado para Enterprise.");
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const updated = await updatePlatformBranchBilling(session.accessToken, selected.branchId, form);
      setOverview((current) => current ? recalculateOverview(current, updated) : current);
      setSelected(updated);
      setForm(toForm(updated));
      setMessage(`Configuracion comercial actualizada para ${updated.branchName}.`);
    } catch (saveError) {
      setError(messageFromError(saveError));
    } finally {
      setSaving(false);
    }
  }

  async function recordPayment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selected || !paymentForm) return;
    if (paymentForm.amount <= 0) {
      setPaymentError("El monto recibido debe ser mayor a cero.");
      return;
    }
    setRecordingPayment(true);
    setPaymentError("");
    try {
      const registered = await recordPlatformBillingPayment(session.accessToken, selected.branchId, paymentForm);
      setOverview((current) => current ? recalculateOverview(current, registered.branch) : current);
      setSelected(registered.branch);
      setForm(toForm(registered.branch));
      setPayments((current) => [registered.payment, ...current]);
      setPaymentForm(toPaymentForm(registered.branch));
      setMessage(`Cobro de ${formatCurrency(registered.payment.amount)} registrado.`);
    } catch (recordError) {
      setPaymentError(messageFromError(recordError));
    } finally {
      setRecordingPayment(false);
    }
  }

  function changeContractedPlan(value: string) {
    if (!form) return;
    const nextPlan = value as PlatformBillingUpdatePayload["contractedPlanCode"];
    setForm({
      ...form,
      contractedPlanCode: nextPlan,
      monthlyPriceOverride: nextPlan === "ENTERPRISE" ? form.monthlyPriceOverride : null,
      implementationFee: selected?.subscriptionStatus === "UNCONFIGURED" ? implementationForPlan(nextPlan) : form.implementationFee,
    });
  }

  return (
    <AppShell
      eyebrow="Plataforma GymFort"
      mode="setup"
      organizationName="GymFort"
      session={session}
      subtitle="Control comercial por cliente y sucursal: socios activos, plan contratado, importe, fechas y estado de cobro."
      title="Cobros y contratos"
      variant="platform"
    >
      {error && !selected ? <FormError message={error} /> : null}

      <section className="platform-facts">
        <div><span>MRR configurado</span><strong>{formatCurrency(summary?.monthlyRecurringRevenue ?? 0)}</strong></div>
        <div><span>Gimnasios cliente</span><strong>{summary?.customerCount ?? 0}</strong></div>
        <div><span>Sucursales</span><strong>{summary?.branchCount ?? 0}</strong></div>
        <div><span>Socios activos</span><strong>{formatNumber(summary?.activeMemberCount ?? 0)}</strong></div>
        <div><span>Usuarios internos</span><strong>{formatNumber(summary?.activeTeamUserCount ?? 0)}</strong></div>
      </section>

      <section className="admin-panel">
        <header className="admin-panel-header">
          <div><p>Cobro por cliente</p><h2>Cuánto debes cobrar a cada gimnasio</h2><span>El total suma las sucursales con suscripción activa dentro de cada Organization.</span></div>
        </header>
        <div className="platform-table">
          <div className="platform-table-row header"><span>Cliente</span><span>Operación</span><span>Total mensual</span><span>Estado</span></div>
          {customerRows.map((customer) => (
            <div className="platform-table-row" key={customer.organizationId}>
              <div><strong>{customer.organizationName}</strong><small className="block text-[var(--brand-muted)]">{customer.branchCount} {customer.branchCount === 1 ? "sucursal" : "sucursales"}</small></div>
              <div><strong>{formatNumber(customer.activeMembers)} activos</strong><small className="block text-[var(--brand-muted)]">{customer.unconfiguredCount ? `${customer.unconfiguredCount} por configurar` : customer.nextBillingLabel}</small></div>
              <div>
                <strong>{formatCurrency(customer.monthlyAmount)}</strong>
                <small className="block text-[var(--brand-muted)]">Importe mensual configurado</small>
                {customer.implementationOutstanding > 0 ? <small className="block text-[var(--brand-muted)]">{formatCurrency(customer.implementationOutstanding)} de implementación pendiente</small> : null}
              </div>
              <button onClick={() => { setQuery(customer.organizationName); setPaymentFilter("ALL"); }} type="button">{customer.overdueCount ? `${customer.overdueCount} vencida${customer.overdueCount === 1 ? "" : "s"}` : "Ver sedes"}</button>
            </div>
          ))}
          {customerRows.length === 0 ? <div className="admin-empty-card">Todavia no hay gimnasios cliente para facturar.</div> : null}
        </div>
      </section>

      <section className="admin-panel">
        <header className="admin-panel-header">
          <div><p>Seguimiento por sucursal</p><h2>Planes, fechas y pagos</h2><span>{summary?.overdueBranchCount ?? 0} {(summary?.overdueBranchCount ?? 0) === 1 ? "vencida" : "vencidas"} · {summary?.unconfiguredBranchCount ?? 0} por configurar.</span></div>
        </header>

        <div className="grid gap-3 md:grid-cols-2">
          <label className="form-field"><span>Buscar gimnasio o sucursal</span><input className="app-input" onChange={(event) => setQuery(event.target.value)} placeholder="Nombre, sucursal o código" value={query} /></label>
          <label className="form-field"><span>Estado de cobro</span><select className="app-input" onChange={(event) => setPaymentFilter(event.target.value)} value={paymentFilter}>
            <option value="ALL">Todos</option><option value="CURRENT">Al corriente</option><option value="DUE_TODAY">Vence hoy</option><option value="OVERDUE">Vencido</option><option value="UNCONFIGURED">Sin configurar</option><option value="PENDING">Pendiente</option><option value="PAUSED">Pausada</option><option value="CANCELLED">Cancelada</option>
          </select></label>
        </div>

        <div className="platform-table">
          <div className="platform-table-row header"><span>Cliente / sucursal</span><span>Uso y plan</span><span>Cobro</span><span>Estado</span></div>
          {filteredBranches.map((branch) => {
            const mismatch = branch.subscriptionStatus !== "UNCONFIGURED" && branch.contractedPlanCode !== branch.suggestedPlanCode;
            return <div className="platform-table-row" key={branch.branchId}>
              <div><strong>{branch.organizationName}</strong><small className="block text-[var(--brand-muted)]">{branch.branchName} · {branch.branchCode}</small></div>
              <div><strong>{formatNumber(branch.activeMembers)} activos</strong><small className="block text-[var(--brand-muted)]">{branch.contractedPlanName}{mismatch ? ` · sugerido: ${branch.suggestedPlanName}` : ""}</small></div>
              <div><strong>{branch.monthlyAmount == null ? "Cotizar" : formatCurrency(branch.monthlyAmount)}</strong><small className="block text-[var(--brand-muted)]">{branch.nextBillingDate ? `Proximo: ${formatDate(branch.nextBillingDate)}` : "Fecha pendiente"}</small></div>
              <button onClick={() => openBilling(branch)} type="button">{branch.subscriptionStatus === "UNCONFIGURED" ? "Configurar" : paymentLabel(branch.paymentStatus)}</button>
            </div>;
          })}
          {filteredBranches.length === 0 ? <div className="admin-empty-card">No hay sucursales que coincidan con los filtros.</div> : null}
        </div>
      </section>

      <AppDrawer description={selected ? `${selected.organizationName} · ${selected.branchName} · ${selected.activeMembers} socios activos.` : undefined} onClose={closeBilling} open={Boolean(selected && form)} title="Configuración comercial" wide>
        {selected && form ? <form className="grid gap-4" onSubmit={submit}>
          {error ? <FormError message={error} /> : null}
          {message ? <AppNotice message={message} tone="success" /> : null}
          <section className="platform-facts">
            <div><span>Socios activos</span><strong>{formatNumber(selected.activeMembers)}</strong></div>
            <div><span>Equipo activo</span><strong>{formatNumber(selected.activeTeamUserCount)}</strong></div>
            <div><span>Planes del gimnasio</span><strong>{formatNumber(selected.activeMembershipPlanCount)}</strong></div>
            <div><span>Plan sugerido</span><strong>{selected.suggestedPlanName}</strong></div>
            <div><span>Precio de lista</span><strong>{selected.suggestedMonthlyPrice == null ? "Cotizar" : formatCurrency(selected.suggestedMonthlyPrice)}</strong></div>
            <div><span>Estado de cobro</span><strong>{paymentLabel(selected.paymentStatus)}</strong></div>
          </section>

          <div className="grid gap-4 md:grid-cols-2">
            <SelectField label="Estado de suscripcion" name="subscriptionStatus" onChange={(value) => setForm({ ...form, subscriptionStatus: value as PlatformBillingUpdatePayload["subscriptionStatus"] })} options={subscriptionOptions} value={form.subscriptionStatus} />
            <SelectField label="Plan contratado" name="contractedPlanCode" onChange={changeContractedPlan} options={planOptions} value={form.contractedPlanCode} />
            <NumberField help="Dia habitual de corte/cobro. Debe estar entre 1 y 31." label="Dia de corte" min="1" name="billingAnchorDay" onChange={(value) => setForm({ ...form, billingAnchorDay: numberValue(value, 1) })} value={String(form.billingAnchorDay)} />
            <DateField label="Proximo cobro" name="nextBillingDate" onChange={(value) => setForm({ ...form, nextBillingDate: value })} required value={form.nextBillingDate} />
            {form.contractedPlanCode === "ENTERPRISE" ? <NumberField help="Precio mensual acordado para esta sucursal Enterprise." label="Precio mensual Enterprise" min="0" name="monthlyPriceOverride" onChange={(value) => setForm({ ...form, monthlyPriceOverride: nullableNumber(value) })} step="0.01" value={form.monthlyPriceOverride == null ? "" : String(form.monthlyPriceOverride)} /> : null}
            <NumberField label="Costo de implementacion" min="0" name="implementationFee" onChange={(value) => setForm({ ...form, implementationFee: numberValue(value, 0) })} step="0.01" value={String(form.implementationFee)} />
          </div>

          <label className="flex items-center gap-3 rounded-lg border border-[var(--brand-line)] bg-white p-4 text-sm font-semibold"><input checked={form.implementationPaid} onChange={(event) => setForm({ ...form, implementationPaid: event.target.checked })} type="checkbox" />Implementacion pagada</label>
          <TextAreaField label="Notas comerciales" name="notes" onChange={(value) => setForm({ ...form, notes: value || null })} placeholder="Acuerdos, condiciones, observaciones de cobro..." required={false} rows={4} value={form.notes ?? ""} />
          <div className="platform-actions"><button className="form-primary-button" disabled={saving} type="submit">{saving ? "Guardando..." : "Guardar configuración"}</button></div>
        </form> : null}

        {selected && paymentForm ? <section className="mt-5 grid gap-4 border-t border-[var(--brand-line)] pt-5">
          <header><p className="section-eyebrow">Registro de cobro</p><h3 className="text-lg font-bold">Registrar pago recibido</h3><p className="text-sm text-[var(--brand-muted)]">Este registro es permanente y deja trazabilidad del cobro recibido por GymFort.</p></header>
          <form className="grid gap-4" onSubmit={recordPayment}>
            {paymentError ? <FormError message={paymentError} /> : null}
            <div className="grid gap-4 md:grid-cols-2">
              <SelectField label="Concepto" name="paymentType" onChange={(value) => setPaymentForm({ ...paymentForm, paymentType: value as PlatformBillingPaymentPayload["paymentType"], amount: defaultPaymentAmount(value, selected) })} options={paymentTypeOptions} value={paymentForm.paymentType} />
              <SelectField label="Método recibido" name="paymentMethod" onChange={(value) => setPaymentForm({ ...paymentForm, paymentMethod: value as PlatformBillingPaymentPayload["paymentMethod"] })} options={paymentMethodOptions} value={paymentForm.paymentMethod} />
              <NumberField label="Monto recibido" min="0.01" name="paymentAmount" onChange={(value) => setPaymentForm({ ...paymentForm, amount: numberValue(value, 0) })} step="0.01" value={String(paymentForm.amount)} />
              <DateField label="Fecha del pago" name="paidOn" onChange={(value) => setPaymentForm({ ...paymentForm, paidOn: value })} value={paymentForm.paidOn} />
              <TextField label="Referencia o folio" name="paymentReference" onChange={(value) => setPaymentForm({ ...paymentForm, reference: value || null })} placeholder="Transferencia, recibo o folio" required={false} value={paymentForm.reference ?? ""} />
            </div>
            <TextAreaField label="Nota del cobro" name="paymentNotes" onChange={(value) => setPaymentForm({ ...paymentForm, notes: value || null })} placeholder="Ej. pago recibido de Juan Pérez, contrato 2026..." required={false} rows={3} value={paymentForm.notes ?? ""} />
            <div className="platform-actions"><button className="form-primary-button" disabled={recordingPayment} type="submit">{recordingPayment ? "Registrando..." : "Registrar pago"}</button></div>
          </form>

          <div className="grid gap-2">
            <h4 className="text-base font-bold">Historial de pagos</h4>
            {payments.map((payment) => <article className="rounded-lg border border-[var(--brand-line)] bg-white p-3" key={payment.id}>
              <div className="flex flex-wrap items-start justify-between gap-2"><div><strong>{formatCurrency(payment.amount)}</strong><span className="ml-2 text-sm text-[var(--brand-muted)]">{paymentTypeLabel(payment.paymentType)} · {paymentMethodLabel(payment.paymentMethod)}</span></div><time className="text-sm font-semibold">{formatDate(payment.paidOn)}</time></div>
              {payment.reference ? <small className="block text-[var(--brand-muted)]">Referencia: {payment.reference}</small> : null}
              {payment.notes ? <small className="block text-[var(--brand-muted)]">{payment.notes}</small> : null}
            </article>)}
            {!paymentError && payments.length === 0 ? <div className="admin-empty-card">Aún no hay cobros registrados para esta sucursal.</div> : null}
          </div>
        </section> : null}
      </AppDrawer>
    </AppShell>
  );
}

function toForm(branch: PlatformBranchBilling): PlatformBillingUpdatePayload {
  const today = isoToday();
  const anchor = branch.billingAnchorDay ?? new Date().getDate();
  return {
    subscriptionStatus: branch.subscriptionStatus === "UNCONFIGURED" ? "ACTIVE" : branch.subscriptionStatus as PlatformBillingUpdatePayload["subscriptionStatus"],
    billingAnchorDay: anchor,
    nextBillingDate: branch.nextBillingDate ?? today,
    contractedPlanCode: branch.contractedPlanCode as PlatformBillingUpdatePayload["contractedPlanCode"],
    monthlyPriceOverride: branch.monthlyPriceOverride,
    implementationFee: branch.subscriptionStatus === "UNCONFIGURED" ? implementationForPlan(branch.suggestedPlanCode) : branch.implementationFee,
    implementationPaid: branch.implementationPaid,
    notes: branch.notes,
    expectedVersion: branch.version,
  };
}

function groupByCustomer(branches: PlatformBranchBilling[]) {
  const groups = new Map<string, {
    organizationId: string;
    organizationName: string;
    branchCount: number;
    activeMembers: number;
    monthlyAmount: number;
    implementationOutstanding: number;
    unconfiguredCount: number;
    overdueCount: number;
    dates: Set<string>;
  }>();
  for (const branch of branches) {
    const group = groups.get(branch.organizationId) ?? {
      organizationId: branch.organizationId,
      organizationName: branch.organizationName,
      branchCount: 0,
      activeMembers: 0,
      monthlyAmount: 0,
      implementationOutstanding: 0,
      unconfiguredCount: 0,
      overdueCount: 0,
      dates: new Set<string>(),
    };
    group.branchCount += 1;
    group.activeMembers += branch.activeMembers;
    if (branch.subscriptionStatus === "ACTIVE" && branch.monthlyAmount != null) group.monthlyAmount += branch.monthlyAmount;
    if (branch.subscriptionStatus !== "UNCONFIGURED" && !branch.implementationPaid) group.implementationOutstanding += branch.implementationFee;
    if (branch.subscriptionStatus === "UNCONFIGURED") group.unconfiguredCount += 1;
    if (branch.paymentStatus === "OVERDUE") group.overdueCount += 1;
    if (branch.subscriptionStatus === "ACTIVE" && branch.nextBillingDate) group.dates.add(branch.nextBillingDate);
    groups.set(branch.organizationId, group);
  }
  return [...groups.values()].map((group) => ({
    ...group,
    nextBillingLabel: group.dates.size === 1 ? `Proximo: ${formatDate([...group.dates][0])}` : group.dates.size > 1 ? "Fechas por sucursal" : "Sin fecha de cobro",
  })).sort((a, b) => b.monthlyAmount - a.monthlyAmount || a.organizationName.localeCompare(b.organizationName));
}

function compareBillingUrgency(a: PlatformBranchBilling, b: PlatformBranchBilling) {
  const priority: Record<string, number> = { OVERDUE: 0, DUE_TODAY: 1, UNCONFIGURED: 2, PENDING: 3, CURRENT: 4, PAUSED: 5, CANCELLED: 6 };
  const difference = (priority[a.paymentStatus] ?? 9) - (priority[b.paymentStatus] ?? 9);
  if (difference !== 0) return difference;
  return (a.nextBillingDate ?? "9999-12-31").localeCompare(b.nextBillingDate ?? "9999-12-31") || a.organizationName.localeCompare(b.organizationName);
}

function recalculateOverview(current: PlatformBillingOverview, updated: PlatformBranchBilling): PlatformBillingOverview {
  const branches = current.branches.map((branch) => branch.branchId === updated.branchId ? updated : branch);
  return { branches, summary: {
    customerCount: new Set(branches.map((branch) => branch.organizationId)).size,
    branchCount: branches.length,
    activeMemberCount: branches.reduce((total, branch) => total + branch.activeMembers, 0),
    activeTeamUserCount: [...new Map(branches.map((branch) => [branch.organizationId, branch.activeTeamUserCount])).values()].reduce((total, count) => total + count, 0),
    monthlyRecurringRevenue: branches.reduce((total, branch) => total + (branch.subscriptionStatus === "ACTIVE" ? branch.monthlyAmount ?? 0 : 0), 0),
    overdueBranchCount: branches.filter((branch) => branch.paymentStatus === "OVERDUE").length,
    unconfiguredBranchCount: branches.filter((branch) => branch.subscriptionStatus === "UNCONFIGURED").length,
  } };
}

function implementationForPlan(planCode: string) { const fees: Record<string, number> = { GYMFORT_150: 2500, GYMFORT_500: 3500, GYMFORT_1000: 5000, GYMFORT_2000: 7500 }; return fees[planCode] ?? 0; }
function paymentLabel(status: string) { const labels: Record<string, string> = { CURRENT: "Al corriente", DUE_TODAY: "Vence hoy", OVERDUE: "Vencido", UNCONFIGURED: "Configurar", PENDING: "Pendiente", PAUSED: "Pausada", CANCELLED: "Cancelada", ACTIVE: "Activa" }; return labels[status] ?? status; }
function formatCurrency(value: number) { return new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN", maximumFractionDigits: 0 }).format(value); }
function formatNumber(value: number) { return new Intl.NumberFormat("es-MX").format(value); }
function formatDate(value: string) { const [year, month, day] = value.split("-"); return year && month && day ? `${day}/${month}/${year}` : value; }
function isoToday() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`; }
function numberValue(value: string, fallback: number) { const parsed = Number(value); return Number.isFinite(parsed) ? parsed : fallback; }
function nullableNumber(value: string) { if (!value.trim()) return null; const parsed = Number(value); return Number.isFinite(parsed) ? parsed : null; }
function toPaymentForm(branch: PlatformBranchBilling): PlatformBillingPaymentPayload { return { paymentType: "SUBSCRIPTION", paymentMethod: "TRANSFER", amount: branch.monthlyAmount ?? 0, paidOn: isoToday(), reference: null, notes: null, idempotencyKey: newIdempotencyKey() }; }
function newIdempotencyKey() { return typeof crypto !== "undefined" && typeof crypto.randomUUID === "function" ? crypto.randomUUID() : `payment-${Date.now()}-${Math.random().toString(36).slice(2)}`; }
function defaultPaymentAmount(paymentType: string, branch: PlatformBranchBilling) { return paymentType === "IMPLEMENTATION" ? branch.implementationFee : paymentType === "SUBSCRIPTION" ? branch.monthlyAmount ?? 0 : 0; }
function paymentTypeLabel(type: string) { const labels: Record<string, string> = { SUBSCRIPTION: "Mensualidad", IMPLEMENTATION: "Implementación", OTHER: "Otro concepto" }; return labels[type] ?? type; }
function paymentMethodLabel(method: string) { const labels: Record<string, string> = { TRANSFER: "Transferencia", CASH: "Efectivo", CARD: "Tarjeta", STRIPE: "Stripe", OTHER: "Otro" }; return labels[method] ?? method; }
function messageFromError(error: unknown) { return error instanceof Error ? error.message : "No se pudo completar la operacion."; }
