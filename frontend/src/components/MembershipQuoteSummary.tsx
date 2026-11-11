import type { MembershipQuote } from "@/lib/api";

export function MembershipQuoteSummary({
  loading,
  detailed = false,
  quote,
}: {
  detailed?: boolean;
  loading: boolean;
  quote: MembershipQuote | null;
}) {
  if (loading) {
    return <p className="text-xs font-medium text-gray-500">Calculando precio y beneficios...</p>;
  }
  if (!quote) return null;

  if (detailed) return (
    <div className="reception-quote">
      <dl>
        <div><dt>Plan</dt><dd>{formatMoney(quote.planPriceCents, quote.currency)}</dd></div>
        {quote.registrationFeeCents > 0 ? <div><dt>{quote.registrationFeeLabel ?? "Inscripción"}</dt><dd>{formatMoney(quote.registrationFeeCents, quote.currency)}</dd></div> : null}
        <div><dt>Descuento</dt><dd>{formatMoney(quote.discountCents, quote.currency)}</dd></div>
        <div className="reception-quote-total"><dt>Total a cobrar</dt><dd>{formatMoney(quote.totalCents, quote.currency)}</dd></div>
      </dl>
      <p>{quote.appliedBenefits.length ? `Beneficios: ${quote.appliedBenefits.map((benefit) => benefit.name).join(", ")}` : "Sin beneficios de precio aplicables."}</p>
    </div>
  );

  return (
    <div className="grid gap-1 text-xs font-medium text-gray-600">
      <p>
        Plan: {formatMoney(quote.planPriceCents, quote.currency)}
        {quote.registrationFeeCents > 0 ? ` · ${quote.registrationFeeLabel ?? "Inscripción"}: ${formatMoney(quote.registrationFeeCents, quote.currency)}` : ""}
        {" · "}Descuento: {formatMoney(quote.discountCents, quote.currency)}
        {" · "}Total: <strong>{formatMoney(quote.totalCents, quote.currency)}</strong>
      </p>
      {quote.appliedBenefits.length > 0 ? (
        <p>Beneficios: {quote.appliedBenefits.map((benefit) => benefit.name).join(", ")}</p>
      ) : (
        <p>Sin beneficios de precio aplicables.</p>
      )}
    </div>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
