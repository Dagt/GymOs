"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import type { FormEvent } from "react";
import { useEffect, useState } from "react";
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
  AuthRequired,
  SaleItemsSection,
  SaleLoadingState,
  SaleMembershipsSection,
  SalePaymentsSection,
  SaleRefundsSection,
  estimatedRefundableCents,
  formatMoney,
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
    const amountCents = refundForm.amount.trim()
      ? Math.round(Number(refundForm.amount) * 100)
      : estimatedRefundableCents(data);
    const succeeded = await runAction(
      () => requestSaleRefund(session.accessToken, data.sale.id, {
        paymentId: refundForm.paymentId || null,
        amountCents,
        reason: refundForm.reason,
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

  if (!session) return <AuthRequired />;
  if (loading || !data) return <SaleLoadingState />;

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/pos">POS</Link>
            <h1 className="mt-1 text-3xl font-semibold">{data.sale.saleNumber}</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">{data.sale.clientName} - {data.sale.status}</p>
          </div>
          <div className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-right">
            <p className="text-xs font-semibold uppercase tracking-wide text-[#51735f]">Saldo</p>
            <p className="text-2xl font-semibold">{formatMoney(data.sale.balanceCents, data.sale.currency)}</p>
          </div>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[1fr_1fr]">
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
        {error ? <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f] lg:col-span-2">{error}</p> : null}
      </section>
    </main>
  );
}

function messageFrom(exception: unknown, fallback: string) {
  return exception instanceof Error ? exception.message : fallback;
}
