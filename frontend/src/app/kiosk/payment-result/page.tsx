"use client";

import { useSearchParams } from "next/navigation";

export default function KioskPaymentResultPage() {
  const params = useSearchParams();
  const status = params.get("status") ?? "pending";
  const successful = status === "success";
  const cancelled = status === "cancelled";

  return (
    <main className="kiosk-screen grid min-h-screen place-items-center px-6 text-white">
      <section className="kiosk-card max-w-xl text-center">
        <p className="app-eyebrow">Pago seguro</p>
        <h1 className="mt-2 text-3xl font-semibold">
          {successful ? "Estamos confirmando tu pago" : cancelled ? "El pago fue cancelado" : "Pago pendiente"}
        </h1>
        <p className="kiosk-muted mt-4">
          {successful
            ? "La membresía se actualizará únicamente cuando GymOS reciba y valide la confirmación firmada de la pasarela."
            : cancelled
              ? "No se realizó ningún cambio en tu membresía."
              : "La pasarela todavía no ha confirmado el resultado. No intentes pagar de nuevo desde esta pantalla."}
        </p>
        <p className="kiosk-feature-note mt-5">
          Por seguridad, vuelve a recepción para consultar el estado final o iniciar una nueva sesión de autoservicio.
        </p>
        <button className="kiosk-secondary-action mt-6" onClick={() => window.close()} type="button">
          Cerrar pestaña
        </button>
      </section>
    </main>
  );
}
