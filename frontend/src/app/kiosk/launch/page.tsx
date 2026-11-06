"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { getStoredSession, launchKioskSession } from "@/lib/api";
import { SESSION_SLOT_KEY } from "@/lib/api/session";
import { encodeKioskLaunch } from "@/lib/kiosk-launch";
import { canLaunchKiosk, getPostLoginPath } from "@/lib/role-model";

export default function KioskLaunchPage() {
  const router = useRouter();
  const [ready, setReady] = useState(false);
  const [launching, setLaunching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const session = getStoredSession();
    if (session && !canLaunchKiosk(session.user)) {
      router.replace(getPostLoginPath(session.user));
      return;
    }
    if (!session?.user.organizationId || !session.user.branchId) {
      setError("Selecciona una sucursal activa en recepción antes de abrir el kiosco.");
    }
    setReady(true);
  }, [router]);

  async function launch() {
    const administrativeSession = getStoredSession();
    if (!administrativeSession || !canLaunchKiosk(administrativeSession.user)) {
      setError("Sólo el personal de recepción puede abrir el kiosco de autoservicio.");
      return;
    }
    if (!administrativeSession?.user.organizationId || !administrativeSession.user.branchId) {
      setError("La sesión de recepción no tiene una organización y sucursal activas.");
      return;
    }
    const kioskWindow = window.open("about:blank", "_blank");
    if (!kioskWindow) {
      setError("El navegador bloqueó la nueva pestaña. Permite ventanas emergentes para GymFort y vuelve a intentar.");
      return;
    }
    kioskWindow.opener = null;
    kioskWindow.sessionStorage.removeItem(SESSION_SLOT_KEY);
    kioskWindow.document.title = "Preparando kiosco...";
    kioskWindow.document.body.innerHTML = "<main style='font-family:sans-serif;display:grid;place-items:center;min-height:100vh'><p>Preparando autoservicio seguro...</p></main>";
    setLaunching(true); setError(null);
    try {
      const kioskSession = await launchKioskSession(administrativeSession.accessToken);
      kioskWindow.name = encodeKioskLaunch(kioskSession);
      kioskWindow.location.replace("/kiosk/reception");
      router.replace("/reception");
    } catch (exception) {
      kioskWindow.close();
      setError(exception instanceof Error ? exception.message : "No fue posible abrir el kiosco.");
    } finally { setLaunching(false); }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-slate-950 px-6 text-white">
      <section className="w-full max-w-2xl rounded-3xl border border-white/10 bg-white/5 p-8 shadow-2xl">
        <p className="text-xs font-black uppercase tracking-[0.25em] text-white/60">Recepción</p>
        <h1 className="mt-3 text-3xl font-black">Abrir kiosco para socios</h1>
        <p className="mt-4 text-white/75">GymFort abrirá una pestaña separada con una sesión limitada. La caja, el POS y la sesión administrativa permanecerán fuera del kiosco.</p>
        <ol className="mt-6 grid gap-3 text-sm text-white/80 sm:grid-cols-3">
          <li className="rounded-xl border border-white/10 bg-white/5 p-4"><strong className="block text-white">1. Abrir</strong>No registres esta computadora ni le asignes un ID.</li>
          <li className="rounded-xl border border-white/10 bg-white/5 p-4"><strong className="block text-white">2. Maximizar</strong>Deja la pestaña a pantalla completa para el socio.</li>
          <li className="rounded-xl border border-white/10 bg-white/5 p-4"><strong className="block text-white">3. Operar</strong>La pantalla se limpia entre socios y conserva la sucursal.</li>
        </ol>
        {error ? <p className="mt-5 rounded-xl bg-red-500/15 p-4 text-sm text-red-100" role="alert">{error}</p> : null}
        <div className="mt-7 flex flex-wrap gap-3">
          <button className="rounded-xl bg-white px-5 py-3 font-black text-slate-950 disabled:opacity-50" disabled={!ready || launching} onClick={launch} type="button">{launching ? "Preparando..." : "Abrir kiosco en otra pestaña"}</button>
          <button className="rounded-xl border border-white/20 px-5 py-3 font-bold" onClick={() => router.replace("/reception")} type="button">Volver a recepción</button>
        </div>
      </section>
    </main>
  );
}
