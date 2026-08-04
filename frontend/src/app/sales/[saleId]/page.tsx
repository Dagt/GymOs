"use client";

import { useParams } from "next/navigation";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  addManualPayment,
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
    setError(null);
    try {
      const response = await addManualPayment(session.accessToken, data.sale.id, {
        method: paymentForm.method,
        amountCents,
        reference: paymentForm.reference,
        receiptObjectKey: null,
        validateNow: paymentForm.method === "CARD_MERCADO_PAGO" ? false : paymentForm.validateNow,
        notes: null,
      });
      if (paymentForm.method === "CARD_MERCADO_PAGO") {
        const payment = [...response.payments].reverse().find((item) => item.method === "CARD_MERCADO_PAGO" && item.status === "PENDING_VALIDATION");
        if (!payment) throw new Error("No se encontro el pago pendiente para enviar a Point.");
        await createMercadoPagoPointOrder(session.accessToken, payment.id);
      }
      setData(response);
      setPaymentForm((current) => ({ ...current, amount: "", reference: "" }));
    } catch (exception) {
      setError(messageFrom(exception, "No fue posible registrar el pago o enviarlo a Point."));
    }
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
    const succeeded = await runAction(
      () => requestSaleRefund(session.accessToken, data.sale.id, {
        paymentId: refundForm.paymentId || null,
        amountCents,
        reason: refundForm.reason,
        items: refundForm.itemized ? items : undefined,
      }),
      "No fue posible registrar reembolso.",
    );
    if (succeeded) setRefundForm(INITIAL_REFUND_FORM);
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
          onRejectReasonChange={setRejectReason}
          onSubmit={submitPayment}
          onUpload={uploadReceipt}
          onValidate={validate}
          rejectReason={rejectReason}
          rejectingPaymentId={rejectingPaymentId}
          uploadingPaymentId={uploadingPaymentId}
          validatingPaymentId={validatingPaymentId}
        />
        <SaleRefundsSection data={data} form={refundForm} onFormChange={setRefundForm} onSubmit={submitRefund} />
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
