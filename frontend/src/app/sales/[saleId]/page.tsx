"use client";

import { useParams } from "next/navigation";
import type { FormEvent } from "react";
import { useEffect, useRef, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { runSaleWrite } from "@/lib/sale-write";
import {
  addManualPayment,
  cancelSale,
  createMercadoPagoPointOrder,
  getPaymentConfiguration,
  getSale,
  getStoredSession,
  rejectPayment,
  requestSaleRefund,
  uploadPaymentReceipt,
  validatePayment,
  type SaleDetailResponse,
  type StoredSession,
} from "@/lib/api";
import {
  SaleItemsSection,
  SaleMembershipsSection,
  SalePaymentsSection,
  SaleRefundsSection,
  estimatedItemizedRefundCents,
  estimatedRefundableCents,
  formatMoney,
  selectedRefundItems,
  type PaymentFormState,
  type RefundFormState,
} from "./SaleDetailSections";

const INITIAL_PAYMENT_FORM: PaymentFormState = {
  method: "CASH",
  amount: "",
  reference: "",
  validateNow: true,
};

const INITIAL_REFUND_FORM: RefundFormState = {
  paymentId: "",
  amount: "",
  reason: "",
  itemized: false,
  items: {},
};

export default function SaleDetailPage() {
  const params = useParams<{ saleId: string }>();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [data, setData] = useState<SaleDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [validatingPaymentId, setValidatingPaymentId] = useState<string | null>(null);
  const [uploadingPaymentId, setUploadingPaymentId] = useState<string | null>(null);
  const [rejectingPaymentId, setRejectingPaymentId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [paymentForm, setPaymentForm] = useState(INITIAL_PAYMENT_FORM);
  const [manualCardEnabled, setManualCardEnabled] = useState(false);
  const [mercadoPagoEnabled, setMercadoPagoEnabled] = useState(false);
  const [refundForm, setRefundForm] = useState(INITIAL_REFUND_FORM);
  const [cancelReason, setCancelReason] = useState("");
  const [writing, setWriting] = useState<"payment" | "refund" | "cancel" | null>(null);
  const writeInFlight = useRef(false);

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!active) return;
      if (!storedSession) {
        setLoading(false);
        return;
      }
      setSession(storedSession);
      try {
        const [response, paymentConfiguration] = await Promise.all([
          getSale(storedSession.accessToken, params.saleId),
          getPaymentConfiguration(storedSession.accessToken),
        ]);
        if (active) {
          setData(response);
          setManualCardEnabled(paymentConfiguration.receptionManualTerminalEnabled);
          setMercadoPagoEnabled(paymentConfiguration.mercadoPagoReady);
        }
      } catch (exception) {
        if (active) setError(messageFrom(exception, "No fue posible cargar venta."));
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadInitial();
    return () => {
      active = false;
    };
  }, [params.saleId]);

  async function validate(paymentId: string) {
    if (!session) return;
    setValidatingPaymentId(paymentId);
    await runAction(
      () => validatePayment(session.accessToken, paymentId),
      "No fue posible validar pago.",
      () => setValidatingPaymentId(null),
    );
  }

  async function uploadReceipt(paymentId: string, file: File | null) {
    if (!session || !file) return;
    setUploadingPaymentId(paymentId);
    await runAction(
      () => uploadPaymentReceipt(session.accessToken, paymentId, file),
      "No fue posible subir comprobante.",
      () => setUploadingPaymentId(null),
    );
  }

  async function reject(paymentId: string) {
    if (!session) return;
    setRejectingPaymentId(paymentId);
    const succeeded = await runAction(
      () => rejectPayment(session.accessToken, paymentId, rejectReason || "Comprobante no valido"),
      "No fue posible rechazar pago.",
      () => setRejectingPaymentId(null),
    );
    if (succeeded) setRejectReason("");
  }

  async function submitPayment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !data) return;
    const amountCents = paymentForm.amount.trim()
      ? Math.round(Number(paymentForm.amount) * 100)
      : data.sale.balanceCents;
    const payload = {
      method: paymentForm.method,
      amountCents,
      reference: paymentForm.reference,
      receiptObjectKey: null,
      validateNow: paymentForm.method === "CARD_MERCADO_PAGO" ? false : paymentForm.validateNow,
      notes: null,
    };
    const succeeded = await runWrite("payment", () => runSaleWrite(session, `payment:${data.sale.id}`, payload, async (idempotencyKey) => {
      const response = await addManualPayment(session.accessToken, data.sale.id, { ...payload, idempotencyKey });
      if (payload.method === "CARD_MERCADO_PAGO") {
        const payment = response.payments.find((item) => item.id === response.operationPaymentId);
        if (!payment) throw new Error("Consulta el historial de la venta antes de volver a cobrar.");
        if (payment.status === "PENDING_VALIDATION") {
          try {
            await createMercadoPagoPointOrder(session.accessToken, payment.id);
          } catch {
            throw new Error("El pago quedó registrado. Falta confirmar el envío a Point.");
          }
        }
      }
      return response;
    }), "No fue posible registrar el pago.");
    if (succeeded) setPaymentForm(INITIAL_PAYMENT_FORM);
  }

  async function submitRefund(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !data) return;
    const items = refundForm.itemized ? selectedRefundItems(data, refundForm) : [];
    if (refundForm.itemized && items.length === 0) {
      setError("Selecciona al menos un producto y una cantidad para devolver.");
      return;
    }
    const suggestedAmount = refundForm.itemized
      ? estimatedItemizedRefundCents(data, refundForm)
      : estimatedRefundableCents(data);
    const amountCents = refundForm.amount.trim()
      ? Math.round(Number(refundForm.amount) * 100)
      : suggestedAmount;
    const payload = {
      paymentId: refundForm.paymentId || null,
      amountCents,
      reason: refundForm.reason,
      items: refundForm.itemized ? items : undefined,
    };
    const succeeded = await runWrite("refund", () => runSaleWrite(session, `refund:${data.sale.id}`, payload,
      (idempotencyKey) => requestSaleRefund(session.accessToken, data.sale.id, { ...payload, idempotencyKey })),
    "No fue posible registrar reembolso.");
    if (succeeded) setRefundForm(INITIAL_REFUND_FORM);
  }

  async function retryPoint(paymentId: string) {
    if (!session || !data) return;
    await runWrite("payment", async () => {
      await createMercadoPagoPointOrder(session.accessToken, paymentId);
      return getSale(session.accessToken, data.sale.id);
    }, "No fue posible recuperar el envío a Point.");
  }

  async function submitCancellation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !data) return;
    await runWrite("cancel", () => cancelSale(session.accessToken, data.sale.id, cancelReason), "No fue posible cancelar la venta.");
  }

  async function runWrite(kind: NonNullable<typeof writing>, action: () => Promise<SaleDetailResponse>, fallback: string) {
    if (writeInFlight.current) return false;
    writeInFlight.current = true;
    setWriting(kind);
    try {
      return await runAction(action, fallback);
    } finally {
      writeInFlight.current = false;
      setWriting(null);
    }
  }

  async function runAction(
    action: () => Promise<SaleDetailResponse>,
    fallbackMessage: string,
    after?: () => void,
  ) {
    setError(null);
    try {
      setData(await action());
      return true;
    } catch (exception) {
      setError(messageFrom(exception, fallbackMessage));
      return false;
    } finally {
      after?.();
    }
  }

  if (loading) return <LoadingScreen text="Cargando venta..." />;
  if (!session) return <AuthRequiredCard message="Necesitas una sesion activa para consultar ventas." title="Inicia sesion para ver esta venta" />;
  if (!data) return <LoadingScreen text="Preparando detalle de venta..." />;

  const mayRefund = session.user.permissions.includes("sale.refund");
  const canCancel = mayRefund && data.sale.businessContext === "RETAIL_SALE" && data.sale.status === "PENDING_PAYMENT"
    && data.sale.paidCents === 0 && data.payments.every((payment) => payment.status === "REJECTED");

  return (
    <AppShell
      eyebrow="Venta"
      session={session}
      subtitle={`${businessContextLabel(data.sale.businessContext)} - ${data.sale.clientName} - ${data.sale.status} - Saldo ${formatMoney(data.sale.balanceCents, data.sale.currency)}`}
      title={data.sale.saleNumber}
    >
      <section className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <SaleItemsSection data={data} />
        <SalePaymentsSection
          data={data}
          form={paymentForm}
          manualCardEnabled={manualCardEnabled}
          mercadoPagoEnabled={mercadoPagoEnabled}
          onFormChange={(next) => setPaymentForm(next.method === "CARD_MERCADO_PAGO" ? { ...next, validateNow: false } : next)}
          onReject={reject}
          onRetryPoint={session.user.permissions.includes("sale.create") ? retryPoint : undefined}
          onRejectReasonChange={setRejectReason}
          onSubmit={submitPayment}
          onUpload={uploadReceipt}
          onValidate={validate}
          rejectReason={rejectReason}
          rejectingPaymentId={rejectingPaymentId}
          uploadingPaymentId={uploadingPaymentId}
          validatingPaymentId={validatingPaymentId}
          canRecord={session.user.permissions.includes("payment.manual.create")}
          writing={writing !== null}
        />
        <SaleRefundsSection data={data} form={refundForm} onFormChange={setRefundForm} onSubmit={submitRefund} canRefund={mayRefund} writing={writing !== null} />
        {canCancel ? (
          <form className="grid gap-3 rounded-2xl border border-gray-100 bg-white p-5" onSubmit={submitCancellation}>
            <label className="grid gap-2 text-sm font-medium">
              Motivo de cancelación
              <input className="h-10 rounded-md border border-gray-200 px-3" value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} required minLength={3} maxLength={500} />
            </label>
            <button className="app-button-secondary" type="submit" disabled={writing !== null || cancelReason.trim().length < 3}>
              {writing === "cancel" ? "Cancelando…" : "Cancelar venta"}
            </button>
          </form>
        ) : null}
        <SaleMembershipsSection data={data} />
        {error ? <InlineError message={error} /> : null}
      </section>
    </AppShell>
  );
}

function messageFrom(exception: unknown, fallback: string) {
  return exception instanceof Error ? exception.message : fallback;
}

function businessContextLabel(context: string) {
  const labels: Record<string, string> = {
    RETAIL_SALE: "Venta de productos",
    MEMBERSHIP_ENROLLMENT: "Contratación de membresía",
    MEMBERSHIP_RENEWAL: "Renovación de membresía",
    MEMBERSHIP_PLAN_CHANGE: "Cambio de plan",
  };
  return labels[context] ?? context;
}
