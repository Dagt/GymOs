import Link from "next/link";

export default function KioskCheckoutResultPage() {
  return (
    <main className="grid min-h-screen place-items-center bg-[var(--brand-surface)] p-6 text-[#13251c]">
      <section className="w-full max-w-xl rounded-3xl border border-[var(--brand-line)] bg-white p-8 text-center shadow-xl">
        <p className="app-eyebrow">Self checkout</p>
        <h1 className="mt-2 text-3xl font-black">Estamos verificando el resultado</h1>
        <p className="mt-4 text-sm leading-6 text-[var(--brand-muted)]">
          El regreso desde Stripe no se toma como comprobante. GymOS confirmará el pago o la
          autorización de domiciliación únicamente cuando reciba el webhook firmado y consulte
          el estado autoritativo en Stripe.
        </p>
        <Link className="app-button mt-7 inline-flex" href="/kiosk/browser">
          Volver al kiosco
        </Link>
      </section>
    </main>
  );
}
