import Link from "next/link";
import type { FormEvent } from "react";
import type {
  ManualPaymentPayload,
  PaymentDetail,
  SaleDetailResponse,
} from "@/lib/api";

export type PaymentFormState = {
  method: ManualPaymentPayload["method"];
  amount: string;
  reference: string;
  validateNow: boolean;
};

export type RefundFormState = {
  paymentId: string;
  amount: string;
  reason: string;
};

export function AuthRequired() {
  return (
    <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
      <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold">Necesitas iniciar sesión</h1>
        <Link className="mt-5 inline-flex h-11 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]" href="/login">
          Iniciar sesion
        </Link>
      </section>
    </main>
  );
}

export function SaleLoadingState() {
  return (
    <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
      <p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">Cargando venta...</p>
    </main>
  );
}

export function SaleItemsSection({ data }: { data: SaleDetailResponse }) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Items</h2>
      <div className="mt-4 grid gap-3">
        {data.items.map((item) => (
          <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={item.id}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-semibold">{item.name}</p>
                <p className="mt-1 text-sm text-[#5d6b63]">{item.quantity} x {formatMoney(item.unitPriceCents, data.sale.currency)}</p>
              </div>
              <p className="font-semibold">{formatMoney(item.totalCents, data.sale.currency)}</p>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

export function SalePaymentsSection({
  data,
  form,
  validatingPaymentId,
  uploadingPaymentId,
  rejectingPaymentId,
  rejectReason,
  onFormChange,
  onSubmit,
  onValidate,
  onUpload,
  onReject,
  onRejectReasonChange,
}: {
  data: SaleDetailResponse;
  form: PaymentFormState;
  validatingPaymentId: string | null;
  uploadingPaymentId: string | null;
  rejectingPaymentId: string | null;
  rejectReason: string;
  onFormChange: (next: PaymentFormState) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onValidate: (paymentId: string) => void;
  onUpload: (paymentId: string, file: File | null) => void;
  onReject: (paymentId: string) => void;
  onRejectReasonChange: (value: string) => void;
}) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Pagos</h2>
      {data.sale.balanceCents > 0 ? (
        <form className="mt-4 grid gap-3 rounded-md border border-[#d8ddd2] bg-[#f6f7f2] p-4" onSubmit={onSubmit}>
          <h3 className="font-semibold">Registrar pago</h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">Metodo
              <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => onFormChange({ ...form, method: event.target.value as ManualPaymentPayload["method"] })} value={form.method}>
                <option value="CASH">Efectivo</option><option value="TRANSFER">Transferencia</option><option value="CARD_MANUAL">Tarjeta manual</option>
              </select>
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">Monto
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => onFormChange({ ...form, amount: event.target.value })} placeholder={(data.sale.balanceCents / 100).toFixed(2)} step="0.01" type="number" value={form.amount} />
            </label>
          </div>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">Referencia
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => onFormChange({ ...form, reference: event.target.value })} value={form.reference} />
          </label>
          <label className="flex items-center gap-2 text-sm font-medium text-[#42514a]">
            <input checked={form.validateNow} onChange={(event) => onFormChange({ ...form, validateNow: event.target.checked })} type="checkbox" /> Validar pago ahora
          </label>
          <button className="h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]" type="submit">Registrar pago</button>
        </form>
      ) : null}
      <div className="mt-4 grid gap-3">
        {data.payments.length === 0 ? <p className="text-sm text-[#5d6b63]">Sin pagos registrados.</p> : null}
        {data.payments.map((payment) => (
          <PaymentCard key={payment.id} payment={payment} validating={validatingPaymentId === payment.id} uploading={uploadingPaymentId === payment.id} rejecting={rejectingPaymentId === payment.id} rejectReason={rejectReason} onValidate={onValidate} onUpload={onUpload} onReject={onReject} onRejectReasonChange={onRejectReasonChange} />
        ))}
      </div>
    </section>
  );
}

function PaymentCard({ payment, validating, uploading, rejecting, rejectReason, onValidate, onUpload, onReject, onRejectReasonChange }: {
  payment: PaymentDetail; validating: boolean; uploading: boolean; rejecting: boolean; rejectReason: string;
  onValidate: (paymentId: string) => void; onUpload: (paymentId: string, file: File | null) => void; onReject: (paymentId: string) => void; onRejectReasonChange: (value: string) => void;
}) {
  return (
    <article className="rounded-md border border-[#d8ddd2] px-4 py-3">
      <div className="flex items-start justify-between gap-3"><div><p className="font-semibold">{payment.method}</p><p className="mt-1 text-sm text-[#5d6b63]">{payment.status} - {payment.reference ?? "Sin referencia"}</p></div><p className="font-semibold">{formatMoney(payment.amountCents, payment.currency)}</p></div>
      {payment.receiptObjectKey ? <p className="mt-2 break-all text-xs text-[#6f7a73]">Comprobante: {payment.receiptObjectKey}</p> : null}
      <label className="mt-3 grid gap-2 text-sm font-medium text-[#42514a]">Comprobante<input className="text-sm" disabled={uploading} onChange={(event) => onUpload(payment.id, event.target.files?.[0] ?? null)} type="file" /></label>
      {payment.status === "PENDING_VALIDATION" ? (
        <div className="mt-3 grid gap-2">
          <button className="h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={validating} onClick={() => onValidate(payment.id)} type="button">{validating ? "Validando..." : "Validar pago"}</button>
          <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => onRejectReasonChange(event.target.value)} placeholder="Motivo de rechazo" value={rejectReason} />
          <button className="h-10 rounded-md border border-[#d99a9a] bg-white px-4 text-sm font-semibold text-[#8c2f2f] disabled:opacity-60" disabled={rejecting} onClick={() => onReject(payment.id)} type="button">{rejecting ? "Rechazando..." : "Rechazar pago"}</button>
        </div>
      ) : null}
    </article>
  );
}

export function SaleRefundsSection({ data, form, onFormChange, onSubmit }: { data: SaleDetailResponse; form: RefundFormState; onFormChange: (next: RefundFormState) => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void; }) {
  const refundable = estimatedRefundableCents(data);
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Reembolsos</h2>
      {refundable > 0 ? (
        <form className="mt-4 grid gap-3 rounded-md border border-[#d8ddd2] bg-[#f6f7f2] p-4" onSubmit={onSubmit}>
          <h3 className="font-semibold">Registrar reembolso manual</h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">Pago relacionado
              <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => onFormChange({ ...form, paymentId: event.target.value })} value={form.paymentId}><option value="">Venta completa</option>{completedPayments(data.payments).map((payment) => <option key={payment.id} value={payment.id}>{payment.method} - {formatMoney(payment.amountCents, payment.currency)}</option>)}</select>
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">Monto<input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" min={0} onChange={(event) => onFormChange({ ...form, amount: event.target.value })} placeholder={(refundable / 100).toFixed(2)} step="0.01" type="number" value={form.amount} /></label>
          </div>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">Motivo<textarea className="min-h-20 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm" onChange={(event) => onFormChange({ ...form, reason: event.target.value })} value={form.reason} /></label>
          <button className="h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={!form.reason.trim()} type="submit">Registrar reembolso</button>
        </form>
      ) : <p className="mt-4 text-sm text-[#5d6b63]">No hay saldo pagado disponible para reembolso.</p>}
      <div className="mt-4 grid gap-3">{data.refunds.length === 0 ? <p className="text-sm text-[#5d6b63]">Sin reembolsos registrados.</p> : null}{data.refunds.map((refund) => <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={refund.id}><div className="flex items-start justify-between gap-3"><div><p className="font-semibold">{refund.status}</p><p className="mt-1 text-sm text-[#5d6b63]">{refund.reason}</p><p className="mt-1 text-xs text-[#6f7a73]">{formatDateTime(refund.requestedAt)}</p></div><p className="font-semibold">{formatMoney(refund.amountCents, refund.currency)}</p></div></article>)}</div>
    </section>
  );
}

export function SaleMembershipsSection({ data }: { data: SaleDetailResponse }) {
  return <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm lg:col-span-2"><h2 className="text-xl font-semibold">Membresias activadas</h2><div className="mt-4 grid gap-3 md:grid-cols-2">{data.memberships.length === 0 ? <p className="text-sm text-[#5d6b63]">Sin membresías activadas por esta venta.</p> : null}{data.memberships.map((membership) => <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={membership.id}><p className="font-semibold">{membership.planName}</p><p className="mt-1 text-sm text-[#5d6b63]">{membership.status} - {membership.startsOn} a {membership.endsOn}</p><p className="mt-1 text-xs text-[#6f7a73]">Gracia hasta {membership.graceEndsOn}</p></article>)}</div></section>;
}

export function formatMoney(cents: number, currency: string) { return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100); }
export function completedPayments(payments: PaymentDetail[]) { return payments.filter((payment) => payment.status === "COMPLETED"); }
/** Presentation estimate only. The backend validates the actual refundable amount. */
export function estimatedRefundableCents(data: SaleDetailResponse) { const refunded = data.refunds.filter((refund) => refund.status === "PROCESSED").reduce((total, refund) => total + refund.amountCents, 0); return Math.max(0, data.sale.paidCents - refunded); }
function formatDateTime(value: string) { return new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)); }
