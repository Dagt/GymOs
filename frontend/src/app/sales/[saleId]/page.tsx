"use client";

import { useParams } from "next/navigation";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  addManualPayment,
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
        const response = await getSale(storedSession.accessToken, params.saleId);
        if (active) setData(response);
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
    const succeeded = await runAction(
      () => addManualPayment(session.accessToken, data.sale.id, {
        method: paymentForm.method,
        amountCents,
        reference: paymentForm.reference,
        receiptObjectKey: null,
        validateNow: paymentForm.validateNow,
        notes: null,
      }),
      "No fue posible registrar pago.",
    );
    if (succeeded) {
      setPaymentForm((current) => ({ ...current, amount: "", reference: "" }));
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
      subtitle={`${data.sale.clientName} - ${data.sale.status} - Saldo ${formatMoney(data.sale.balanceCents, data.sale.currency)}`}
      title={data.sale.saleNumber}
    >
      <section className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <SaleItemsSection data={data} />
        <SalePaymentsSection
          data={data}
          form={paymentForm}
          onFormChange={setPaymentForm}
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
