import type { MembershipQuote } from "@/lib/api";

export function MembershipQuoteSummary({
  loading,
  quote,
}: {
  loading: boolean;
  quote: MembershipQuote | null;
}) {
  if (loading) {
    return <p className="text-xs font-medium text-gray-500">Calculando precio y beneficios...</p>;
  }
  if (!quote) return null;

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
