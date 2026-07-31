"use client";

import { useSearchParams } from "next/navigation";

export default function KioskClosedPage() {
  const searchParams = useSearchParams();
  const inactive = searchParams.get("reason") === "inactive";

  return (
    <main className="min-h-screen grid place-items-center bg-slate-950 px-6 text-white">
      <section className="w-full max-w-xl rounded-3xl border border-white/10 bg-white/5 p-8 text-center shadow-2xl">
        <p className="text-xs font-black uppercase tracking-[0.25em] text-white/60">Autoservicio seguro</p>
        <h1 className="mt-3 text-3xl font-black">
          {inactive ? "Sesión cerrada por inactividad" : "Sesión finalizada"}
        </h1>
        <p className="mt-4 text-white/75">
          {inactive
            ? "Para proteger tus datos, el kiosco cerró la sesión después de tres minutos sin actividad."
            : "Tus datos dejaron de mostrarse y la sesión de autoservicio fue cerrada."}
        </p>
        <p className="mt-6 rounded-xl bg-white/10 p-4 text-sm text-white/70">
          Solicita al personal de recepción que vuelva a abrir el modo kiosco para iniciar una nueva sesión.
        </p>
        <button
          className="mt-7 rounded-xl border border-white/20 px-5 py-3 font-bold text-white"
          onClick={() => window.close()}
          type="button"
        >
          Cerrar esta pestaña
        </button>
      </section>
    </main>
  );
}
