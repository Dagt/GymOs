"use client";

import { useSearchParams } from "next/navigation";

const COPY = {
  inactive: {
    title: "Sesión cerrada por inactividad",
    detail: "Para proteger tus datos, el kiosco cerró la sesión después de tres minutos sin actividad.",
  },
  expired: {
    title: "La sesión expiró",
    detail: "La sesión alcanzó su tiempo máximo de seguridad y ya no puede seguir utilizándose.",
  },
  closed: {
    title: "La sesión ya estaba cerrada",
    detail: "Esta sesión de autoservicio terminó previamente y no puede volver a abrirse con el mismo enlace.",
  },
  manual: {
    title: "Sesión finalizada",
    detail: "Tus datos dejaron de mostrarse y la sesión de autoservicio fue cerrada.",
  },
} as const;

type ClosureReason = keyof typeof COPY;

export default function KioskClosedPage() {
  const searchParams = useSearchParams();
  const rawReason = searchParams.get("reason");
  const reason: ClosureReason = rawReason && rawReason in COPY ? rawReason as ClosureReason : "manual";
  const copy = COPY[reason];

  return (
    <main className="min-h-screen grid place-items-center bg-slate-950 px-6 text-white">
      <section className="w-full max-w-xl rounded-3xl border border-white/10 bg-white/5 p-8 text-center shadow-2xl">
        <p className="text-xs font-black uppercase tracking-[0.25em] text-white/60">Autoservicio seguro</p>
        <h1 className="mt-3 text-3xl font-black">{copy.title}</h1>
        <p className="mt-4 text-white/75">{copy.detail}</p>
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
