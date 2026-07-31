"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { createReceptionKioskSession } from "@/lib/api/selfservice";
import { getStoredSession } from "@/lib/api";
import { encodeKioskLaunch } from "@/lib/kiosk-launch";

export default function KioskLaunchPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [launching, setLaunching] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const session = getStoredSession();
    if (!session?.user.organizationId || !session.user.branchId) {
      setError("Selecciona una sucursal activa en recepción antes de abrir el kiosco.");
    }
    setReady(true);
  }, []);

  async function launch() {
    const administrativeSession = getStoredSession();
    if (!administrativeSession?.user.organizationId || !administrativeSession.user.branchId) {
      setError("La sesión de recepción no tiene una organización y sucursal activas.");
      return;
    }

    const kioskWindow = window.open("about:blank", "_blank");
    if (!kioskWindow) {
      setError("El navegador bloqueó la nueva pestaña. Permite ventanas emergentes para GymOS y vuelve a intentar.");
      return;
    }

    kioskWindow.opener = null;
    kioskWindow.document.title = "Preparando kiosco...";
    kioskWindow.document.body.innerHTML = "<main style='font-family:sans-serif;display:grid;place-items:center;min-height:100vh'><p>Preparando autoservicio seguro...</p></main>";

    setLaunching(true);
    setError(null);
    try {
      const kioskSession = await createReceptionKioskSession(administrativeSession.accessToken);
      const handoff = encodeKioskLaunch(kioskSession);
      kioskWindow.location.replace(`/kiosk/reception#launch=${handoff}`);
      router.replace("/reception");
    } catch (exception) {
      kioskWindow.close();
      setError(exception instanceof Error ? exception.message : "No fue posible abrir el kiosco.");
    } finally {
      setLaunching(false);
    }
  }

  return (
    <main className="min-h-screen grid place-items-center bg-slate-950 px-6 text-white">
      <section className="w-full max-w-xl rounded-3xl border border-white/10 bg-white/5 p-8 shadow-2xl">
        <p className="text-xs font-black uppercase tracking-[0.25em] text-white/60">Recepción</p>
        <h1 className="mt-3 text-3xl font-black">Abrir modo kiosco</h1>
        <p className="mt-4 text-white/75">
          Se abrirá una pestaña separada con una sesión limitada de autoservicio. La sesión administrativa de recepción no se utilizará dentro del kiosco.
        </p>
        {error ? <p className="mt-5 rounded-xl bg-red-500/15 p-4 text-sm text-red-100">{error}</p> : null}
        <div className="mt-7 flex flex-wrap gap-3">
          <button
            className="rounded-xl bg-white px-5 py-3 font-black text-slate-950 disabled:cursor-not-allowed disabled:opacity-50"
            disabled={!ready || launching || Boolean(error)}
            onClick={launch}
            type="button"
          >
            {launching ? "Preparando..." : "Abrir kiosco en otra pestaña"}
          </button>
          <button
            className="rounded-xl border border-white/20 px-5 py-3 font-bold text-white"
            onClick={() => router.replace("/reception")}
            type="button"
          >
            Volver a recepción
          </button>
        </div>
      </section>
    </main>
  );
}
