"use client";

import { CSSProperties, useEffect, useState } from "react";

import {
  getKioskAutopayMandate,
  getKioskSummary,
  type KioskAutopayMandate,
  type KioskPaymentActivity,
  type KioskSessionResponse,
} from "@/lib/api";
import {
  classifyKioskAutopayResult,
  clearKioskAutopayReturn,
  clearKioskPaymentReturn,
  matchesStripeCheckoutSession,
  readKioskAutopayReturn,
  readKioskBrowserSession,
  readKioskPaymentReturn,
  type KioskAutopayReturnContext,
  type KioskPaymentReturnContext,
} from "@/lib/kiosk-autopay-return";

const POLL_ATTEMPTS = 12;
const POLL_INTERVAL_MS = 1_500;

type ResultPhase = "CHECKING" | "SUCCESS" | "PENDING" | "CANCELLED" | "FAILED" | "UNAVAILABLE";
type PollKind = "AUTOPAY" | "PAYMENT";

type ResultView = {
  phase: ResultPhase;
  title: string;
  message: string;
  mandate?: KioskAutopayMandate | null;
  payment?: KioskPaymentActivity | null;
};

const checkingView: ResultView = {
  phase: "CHECKING",
  title: "Confirmando tu domiciliación",
  message: "Stripe ya nos devolvió al kiosco. Estamos esperando la confirmación segura del banco.",
};

const paymentCheckingView: ResultView = {
  phase: "CHECKING",
  title: "Estamos verificando el resultado",
  message: "Stripe ya nos devolvió al kiosco. Estamos esperando la confirmación segura del pago.",
};

export default function KioskCheckoutResultPage() {
  const [session, setSession] = useState<KioskSessionResponse | null>(null);
  const [context, setContext] = useState<KioskAutopayReturnContext | null>(null);
  const [paymentContext, setPaymentContext] = useState<KioskPaymentReturnContext | null>(null);
  const [pollKind, setPollKind] = useState<PollKind | null>(null);
  const [retryCycle, setRetryCycle] = useState(0);
  const [view, setView] = useState<ResultView>(checkingView);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const params = new URLSearchParams(window.location.search);
      const returnStatus = params.get("status");
      const storedContext = readKioskAutopayReturn(window.sessionStorage);
      const storedPaymentContext = readKioskPaymentReturn(window.sessionStorage);
      const storedSession = readKioskBrowserSession(window.sessionStorage);
      setContext(storedContext);
      setPaymentContext(storedPaymentContext);
      setSession(storedSession);

      if (returnStatus === "autopay-cancelled" || returnStatus === "cancelled") {
        setView({
          phase: "CANCELLED",
          title: returnStatus === "autopay-cancelled" ? "Configuración cancelada" : "Pago cancelado",
          message: returnStatus === "autopay-cancelled"
            ? "No guardamos una tarjeta nueva ni modificamos tu autorización de domiciliación."
            : "Stripe no confirmó ningún pago y no realizamos cambios en tu membresía.",
        });
        return;
      }
      if (returnStatus === "success") {
        if (!storedPaymentContext || !storedSession || storedPaymentContext.sessionId !== storedSession.sessionId) {
          setView({
            phase: "UNAVAILABLE",
            title: "La sesión del pago ya no está disponible",
            message: "Por seguridad no podemos consultar datos de otra sesión. Solicita apoyo en recepción.",
          });
          return;
        }
        setView(paymentCheckingView);
        setPollKind("PAYMENT");
        return;
      }
      if (returnStatus !== "autopay-success") {
        setView({
          phase: "UNAVAILABLE",
          title: "No reconocemos este resultado",
          message: "Abre nuevamente el kiosco desde recepción para iniciar una operación segura.",
        });
        return;
      }
      if (!storedContext || !storedSession || storedContext.sessionId !== storedSession.sessionId) {
        setView({
          phase: "UNAVAILABLE",
          title: "La sesión del kiosco ya no está disponible",
          message: "Por seguridad no podemos consultar datos de otra sesión. Solicita un nuevo acceso en recepción.",
        });
        return;
      }
      if (!matchesStripeCheckoutSession(storedContext, params.get("session_id"))) {
        setView({
          phase: "FAILED",
          title: "El resultado no corresponde a esta operación",
          message: "No realizamos cambios desde esta pantalla. Solicita apoyo en recepción para revisar la referencia de Stripe.",
        });
        return;
      }
      setPollKind("AUTOPAY");
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (pollKind !== "AUTOPAY" || !session || !context) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let attempts = 0;

    async function checkMandate() {
      attempts += 1;
      try {
        const mandate = await getKioskAutopayMandate(
          session!.sessionId,
          session!.sessionToken,
          context!.membershipId,
        );
        if (!active) return;
        const result = classifyKioskAutopayResult(mandate);
        if (result === "SUCCESS") {
          setView({
            phase: "SUCCESS",
            title: "Domiciliación activada",
            message: "Stripe confirmó la tarjeta. Quedó autorizada para las próximas renovaciones de tu membresía.",
            mandate,
          });
          return;
        }
        if (result === "FAILED") {
          setView({
            phase: "FAILED",
            title: "No pudimos activar la domiciliación",
            message: mandate?.recoveryMessage
              ?? mandate?.lastError
              ?? "Stripe no confirmó una autorización activa. No se iniciarán cargos automáticos con esta operación.",
            mandate,
          });
          return;
        }
      } catch (error) {
        if (!active) return;
        if (attempts >= POLL_ATTEMPTS) {
          setView({
            phase: "FAILED",
            title: "No pudimos consultar la confirmación",
            message: friendlyError(error),
          });
          return;
        }
      }

      if (attempts >= POLL_ATTEMPTS) {
        setView({
          phase: "PENDING",
          title: "La confirmación sigue pendiente",
          message: "No necesitas volver a capturar tu tarjeta. Puedes comprobar nuevamente o solicitar apoyo en recepción.",
        });
        return;
      }
      timer = setTimeout(() => void checkMandate(), POLL_INTERVAL_MS);
    }

    void checkMandate();
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [context, pollKind, retryCycle, session]);

  useEffect(() => {
    if (pollKind !== "PAYMENT" || !session || !paymentContext) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let attempts = 0;

    async function checkPayment() {
      attempts += 1;
      try {
        const summary = await getKioskSummary(session!.sessionId, session!.sessionToken);
        if (!active) return;
        const payment = summary.paymentActivity.find((item) =>
          item.id === paymentContext!.paymentId || item.saleId === paymentContext!.saleId);
        if (payment?.status === "COMPLETED") {
          setView({
            phase: "SUCCESS",
            title: "Pago confirmado",
            message: "Stripe confirmó el cargo. Tu operación de membresía quedó registrada de forma segura.",
            payment,
          });
          return;
        }
        if (payment && ["REJECTED", "FAILED", "EXPIRED", "CANCELLED"].includes(payment.status)) {
          setView({
            phase: "FAILED",
            title: "El pago no fue completado",
            message: "No realizamos cambios en tu membresía. Puedes intentarlo nuevamente desde el kiosco o solicitar apoyo en recepción.",
            payment,
          });
          return;
        }
      } catch (error) {
        if (!active) return;
        if (attempts >= POLL_ATTEMPTS) {
          setView({
            phase: "FAILED",
            title: "No pudimos consultar el pago",
            message: friendlyError(error),
          });
          return;
        }
      }

      if (attempts >= POLL_ATTEMPTS) {
        setView({
          phase: "PENDING",
          title: "El pago sigue en confirmación",
          message: "No vuelvas a pagar todavía. Comprueba nuevamente o solicita apoyo en recepción.",
        });
        return;
      }
      timer = setTimeout(() => void checkPayment(), POLL_INTERVAL_MS);
    }

    void checkPayment();
    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [paymentContext, pollKind, retryCycle, session]);

  const returnPath = context?.returnPath ?? paymentContext?.returnPath ?? "/kiosk/launch";
  const canRetry = view.phase === "PENDING" || (view.phase === "FAILED" && pollKind !== null);
  const style = {
    "--brand-primary": session?.primaryColor ?? undefined,
    "--brand-accent": session?.accentColor ?? undefined,
    "--brand-surface": session?.surfaceColor ?? undefined,
  } as CSSProperties;

  function returnToKiosk() {
    clearKioskAutopayReturn(window.sessionStorage);
    clearKioskPaymentReturn(window.sessionStorage);
    window.location.assign(returnPath);
  }

  function retry() {
    setView(pollKind === "PAYMENT" ? paymentCheckingView : checkingView);
    setRetryCycle((value) => value + 1);
  }

  return (
    <main
      className="grid min-h-screen place-items-center bg-[radial-gradient(circle_at_top_left,var(--brand-primary),#08171c_55%,#050d10)] p-5 text-white"
      style={style}
    >
      <section className="w-full max-w-2xl overflow-hidden rounded-[2rem] border border-white/20 bg-[#102a32]/95 shadow-2xl backdrop-blur">
        <div className="h-2 bg-[var(--brand-accent)]" />
        <div className="p-7 text-center sm:p-10">
          <p className="text-xs font-black uppercase tracking-[0.18em] text-[var(--brand-primary)]">Pago seguro con Stripe</p>
          <StatusMark phase={view.phase} />
          <h1 className="mt-5 text-3xl font-black tracking-tight sm:text-4xl">{view.title}</h1>
          <p className="mx-auto mt-4 max-w-lg text-base leading-7 text-white/75">{view.message}</p>

          {view.phase === "CHECKING" ? (
            <div className="mx-auto mt-7 flex max-w-md items-center justify-center gap-3 rounded-2xl border border-white/15 bg-white/10 px-5 py-4 text-sm font-bold text-white/80" role="status">
              <span className="h-5 w-5 animate-spin rounded-full border-2 border-[#aab7af] border-t-[var(--brand-primary)]" />
              Esta comprobación puede tardar unos segundos
            </div>
          ) : null}

          {view.phase === "SUCCESS" && view.mandate ? <MandateSummary mandate={view.mandate} /> : null}
          {view.phase === "SUCCESS" && view.payment ? <PaymentSummary payment={view.payment} /> : null}

          <div className="mt-8 grid gap-3 sm:grid-cols-2">
            {canRetry ? (
              <button
                className="min-h-14 rounded-2xl border-2 border-[var(--brand-primary)] px-5 py-3 font-black text-[var(--brand-primary)] transition hover:bg-[#f1f5f2]"
                onClick={retry}
                type="button"
              >
                Comprobar nuevamente
              </button>
            ) : null}
            <button
              className={`min-h-14 rounded-2xl bg-[var(--brand-accent)] px-5 py-3 font-black text-[#13251c] shadow-sm transition hover:brightness-95 ${canRetry ? "sm:col-start-2" : "sm:col-span-2"}`}
              onClick={returnToKiosk}
              type="button"
            >
              Volver al kiosco
            </button>
          </div>

          <p className="mt-6 text-xs leading-5 text-white/55">
            GymFort confirma pagos y autorizaciones únicamente mediante el webhook firmado y el estado consultado directamente en Stripe.
          </p>
        </div>
      </section>
    </main>
  );
}

function StatusMark({ phase }: { phase: ResultPhase }) {
  const appearance = phase === "SUCCESS"
    ? { symbol: "✓", background: "bg-[#dff4e5]", foreground: "text-[#237a3b]", label: "Confirmada" }
    : phase === "CANCELLED"
      ? { symbol: "—", background: "bg-[#eef1ef]", foreground: "text-[#526058]", label: "Cancelada" }
      : phase === "FAILED" || phase === "UNAVAILABLE"
        ? { symbol: "!", background: "bg-[#f9e4e1]", foreground: "text-[#9c3328]", label: "Requiere atención" }
        : { symbol: "…", background: "bg-[#fff5bf]", foreground: "text-[#6d5800]", label: "En proceso" };
  return (
    <div
      aria-label={appearance.label}
      className={`mx-auto mt-6 grid h-20 w-20 place-items-center rounded-full text-4xl font-black ${appearance.background} ${appearance.foreground}`}
    >
      {appearance.symbol}
    </div>
  );
}

function MandateSummary({ mandate }: { mandate: KioskAutopayMandate }) {
  return (
    <dl className="mx-auto mt-7 grid max-w-lg gap-3 rounded-2xl border border-white/15 bg-white/10 p-5 text-left sm:grid-cols-2">
      <div>
        <dt className="text-xs font-black uppercase tracking-wide text-white/55">Tarjeta autorizada</dt>
        <dd className="mt-1 font-black text-white">{mandate.brand?.toUpperCase() ?? "Tarjeta"} •••• {mandate.lastFour ?? "----"}</dd>
      </div>
      <div>
        <dt className="text-xs font-black uppercase tracking-wide text-white/55">Próximo cargo</dt>
        <dd className="mt-1 font-black text-white">{formatDay(mandate.nextChargeOn)}</dd>
      </div>
    </dl>
  );
}

function PaymentSummary({ payment }: { payment: KioskPaymentActivity }) {
  return (
    <div className="mx-auto mt-7 max-w-lg rounded-2xl border border-white/15 bg-white/10 p-5 text-left">
      <p className="text-xs font-black uppercase tracking-wide text-white/55">Importe confirmado</p>
      <p className="mt-1 text-2xl font-black text-white">{formatMoney(payment.amountCents, payment.currency)}</p>
      {payment.receiptUrl ? (
        <a className="mt-3 inline-flex font-black text-[var(--brand-primary)] underline" href={payment.receiptUrl} rel="noreferrer" target="_blank">
          Ver comprobante de Stripe
        </a>
      ) : null}
    </div>
  );
}

function formatDay(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "long", timeZone: "UTC" })
    .format(new Date(`${value}T00:00:00Z`));
}

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(value / 100);
}

function friendlyError(error: unknown) {
  if (!(error instanceof Error) || !error.message || error.message.length > 180) {
    return "La sesión puede haber vencido. Solicita apoyo en recepción antes de volver a capturar la tarjeta.";
  }
  return error.message;
}
