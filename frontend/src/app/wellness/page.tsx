"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import {
  createProfessional,
  createProfessionalAvailability,
  createProfessionalService,
  getProfessionalAvailability,
  getProfessionals,
  getProfessionalServices,
  getStoredSession,
  type ProfessionalAvailability,
  type ProfessionalServiceSummary,
  type ProfessionalSummary,
  type StoredSession,
} from "@/lib/api";

export default function WellnessPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [professionals, setProfessionals] = useState<ProfessionalSummary[]>([]);
  const [services, setServices] = useState<ProfessionalServiceSummary[]>([]);
  const [availability, setAvailability] = useState<ProfessionalAvailability[]>([]);
  const [selectedProfessionalId, setSelectedProfessionalId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [professionalType, setProfessionalType] = useState("NUTRITIONIST");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [serviceName, setServiceName] = useState("");
  const [serviceType, setServiceType] = useState("NUTRITION");
  const [durationMinutes, setDurationMinutes] = useState(60);
  const [price, setPrice] = useState("450");
  const [dayOfWeek, setDayOfWeek] = useState(1);
  const [opensAt, setOpensAt] = useState("09:00");
  const [closesAt, setClosesAt] = useState("14:00");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!active) {
        return;
      }
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const loaded = await getProfessionals(stored.accessToken);
        if (!active) {
          return;
        }
        setProfessionals(loaded);
        const firstId = loaded[0]?.id ?? "";
        setSelectedProfessionalId(firstId);
        if (firstId) {
          const [loadedServices, loadedAvailability] = await Promise.all([
            getProfessionalServices(stored.accessToken, firstId),
            getProfessionalAvailability(stored.accessToken, firstId),
          ]);
          if (active) {
            setServices(loadedServices);
            setAvailability(loadedAvailability);
          }
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar bienestar.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, []);

  async function refresh(activeSession = session, professionalId = selectedProfessionalId) {
    if (!activeSession) {
      return;
    }
    const loaded = await getProfessionals(activeSession.accessToken);
    setProfessionals(loaded);
    const nextId = professionalId || loaded[0]?.id || "";
    setSelectedProfessionalId(nextId);
    if (nextId) {
      const [loadedServices, loadedAvailability] = await Promise.all([
        getProfessionalServices(activeSession.accessToken, nextId),
        getProfessionalAvailability(activeSession.accessToken, nextId),
      ]);
      setServices(loadedServices);
      setAvailability(loadedAvailability);
    }
  }

  async function selectProfessional(professionalId: string) {
    setSelectedProfessionalId(professionalId);
    if (!session) {
      return;
    }
    const [loadedServices, loadedAvailability] = await Promise.all([
      getProfessionalServices(session.accessToken, professionalId),
      getProfessionalAvailability(session.accessToken, professionalId),
    ]);
    setServices(loadedServices);
    setAvailability(loadedAvailability);
  }

  async function submitProfessional(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await createProfessional(session.accessToken, {
        displayName,
        professionalType,
        email,
        phone,
      });
      setDisplayName("");
      setEmail("");
      setPhone("");
      setMessage(`${created.displayName} creado.`);
      await refresh(session, created.id);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear profesional.");
    } finally {
      setSaving(false);
    }
  }

  async function submitService(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selectedProfessionalId) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await createProfessionalService(session.accessToken, selectedProfessionalId, {
        name: serviceName,
        serviceType,
        durationMinutes,
        priceCents: Math.round(Number(price) * 100),
        currency: "MXN",
      });
      setServiceName("");
      setMessage(`${created.name} agregado.`);
      await refresh(session, selectedProfessionalId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear servicio.");
    } finally {
      setSaving(false);
    }
  }

  async function submitAvailability(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selectedProfessionalId) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await createProfessionalAvailability(session.accessToken, selectedProfessionalId, {
        dayOfWeek,
        opensAt,
        closesAt,
      });
      setMessage("Disponibilidad agregada.");
      await refresh(session, selectedProfessionalId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible agregar disponibilidad.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <Centered text="Cargando bienestar..." />;
  }

  if (!session) {
    return <AuthRequired />;
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-3 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/reception">Recepcion</Link>
            <h1 className="mt-1 text-3xl font-semibold">Bienestar</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">Profesionales, servicios y disponibilidad.</p>
          </div>
          <button className="h-10 rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a]" onClick={() => refresh()} type="button">Actualizar</button>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[0.9fr_1.1fr]">
        <aside className="grid gap-6">
          {error ? <Notice tone="error" text={error} /> : null}
          {message ? <Notice tone="ok" text={message} /> : null}
          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitProfessional}>
            <h2 className="text-xl font-semibold">Nuevo profesional</h2>
            <div className="mt-4 grid gap-3">
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setDisplayName(event.target.value)} placeholder="Nombre" value={displayName} />
              <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => setProfessionalType(event.target.value)} value={professionalType}>
                <option value="NUTRITIONIST">Nutriologo</option>
                <option value="PHYSIOTHERAPIST">Fisioterapeuta</option>
                <option value="CHIROPRACTOR">Quiropractico</option>
                <option value="WELLNESS">Bienestar</option>
              </select>
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setEmail(event.target.value)} placeholder="Correo" value={email} />
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setPhone(event.target.value)} placeholder="Telefono" value={phone} />
            </div>
            <button className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={saving || !displayName.trim()} type="submit">Crear</button>
          </form>

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Profesionales</h2>
            <div className="mt-4 grid gap-3">
              {professionals.map((professional) => (
                <button className={`rounded-md border p-4 text-left ${selectedProfessionalId === professional.id ? "border-[#51735f] bg-[#eef7f0]" : "border-[#d8ddd2]"}`} key={professional.id} onClick={() => selectProfessional(professional.id)} type="button">
                  <p className="font-semibold">{professional.displayName}</p>
                  <p className="mt-1 text-sm text-[#5d6b63]">{professional.professionalType} - {professional.status}</p>
                </button>
              ))}
            </div>
          </section>
        </aside>

        <section className="grid gap-6">
          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitService}>
            <h2 className="text-xl font-semibold">Servicio</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setServiceName(event.target.value)} placeholder="Nombre" value={serviceName} />
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setServiceType(event.target.value)} placeholder="Tipo" value={serviceType} />
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" min={15} onChange={(event) => setDurationMinutes(Number(event.target.value))} type="number" value={durationMinutes} />
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" min={0} onChange={(event) => setPrice(event.target.value)} step="0.01" type="number" value={price} />
            </div>
            <button className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={saving || !selectedProfessionalId || !serviceName.trim()} type="submit">Agregar servicio</button>
          </form>

          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitAvailability}>
            <h2 className="text-xl font-semibold">Disponibilidad</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-3">
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" max={7} min={1} onChange={(event) => setDayOfWeek(Number(event.target.value))} type="number" value={dayOfWeek} />
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setOpensAt(event.target.value)} type="time" value={opensAt} />
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setClosesAt(event.target.value)} type="time" value={closesAt} />
            </div>
            <button className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={saving || !selectedProfessionalId} type="submit">Agregar horario</button>
          </form>

          <section className="grid gap-6 md:grid-cols-2">
            <Panel title="Servicios" items={services.map((service) => `${service.name} - ${formatMoney(service.priceCents, service.currency)} - ${service.durationMinutes} min`)} />
            <Panel title="Horarios" items={availability.map((slot) => `Dia ${slot.dayOfWeek}: ${slot.opensAt} - ${slot.closesAt}`)} />
          </section>
        </section>
      </section>
    </main>
  );
}

function Panel({ title, items }: { title: string; items: string[] }) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">{title}</h2>
      <div className="mt-4 grid gap-2 text-sm text-[#42514a]">
        {items.length === 0 ? <p>Sin registros.</p> : items.map((item) => <p className="rounded-md border border-[#e4e8df] px-3 py-2" key={item}>{item}</p>)}
      </div>
    </section>
  );
}

function Notice({ tone, text }: { tone: "ok" | "error"; text: string }) {
  const styles = tone === "ok" ? "border-[#bad9c4] bg-[#eff8f1] text-[#31563c]" : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-md border px-3 py-2 text-sm ${styles}`}>{text}</p>;
}

function Centered({ text }: { text: string }) {
  return <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]"><p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">{text}</p></main>;
}

function AuthRequired() {
  return <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]"><Link className="rounded-md bg-[#13251c] px-4 py-3 text-sm font-semibold text-white" href="/login">Iniciar sesion</Link></main>;
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}
