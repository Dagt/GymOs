"use client";

import { FormEvent, type ReactNode, useEffect, useState } from "react";
import { AuthRequired } from "@/components/AuthRequired";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import {
  createProfessional,
  createProfessionalAvailability,
  createProfessionalService,
  getBranches,
  getCurrentOrganization,
  getProfessionalAvailability,
  getProfessionals,
  getProfessionalServices,
  getStoredSession,
  type ProfessionalAvailability,
  type ProfessionalServiceSummary,
  type ProfessionalSummary,
  type StoredSession,
} from "@/lib/api";

type Organization = Awaited<ReturnType<typeof getCurrentOrganization>>;
type Branch = Awaited<ReturnType<typeof getBranches>>[number];

export default function WellnessPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<Organization | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);
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
        const [loadedOrganization, loadedBranches, loadedProfessionals] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken),
          getProfessionals(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(loadedOrganization);
        setBranches(loadedBranches);
        setProfessionals(loadedProfessionals);
        const firstId = loadedProfessionals[0]?.id ?? "";
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
          setError(exception instanceof Error ? exception.message : "No fue posible cargar servicios.");
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
    } else {
      setServices([]);
      setAvailability([]);
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
    return <LoadingScreen text="Cargando servicios..." />;
  }

  if (!session) {
    return <AuthRequired title="Inicia sesion para administrar servicios" />;
  }

  const activeBranch = branches.find((branch) => branch.id === session.user.branchId) ?? null;

  return (
    <AppShell
      branchName={activeBranch?.name ?? null}
      eyebrow="Servicios"
      mode={activeBranch ? "operation" : "setup"}
      organizationName={organization?.tradeName ?? null}
      session={session}
      subtitle="Profesionales, servicios y disponibilidad del gimnasio activo."
      title="Servicios de bienestar"
    >
      {error ? <InlineError message={error} /> : null}
      {message ? <Notice tone="ok" text={message} /> : null}

      <section className="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
        <aside className="grid gap-5">
          <form className="admin-panel" onSubmit={submitProfessional}>
            <div className="admin-panel-header">
              <div>
                <p>Equipo externo o interno</p>
                <h2>Nuevo profesional</h2>
              </div>
            </div>
            <div className="mt-4 grid gap-3">
              <Field label="Nombre visible">
                <input
                  className="app-input"
                  onChange={(event) => setDisplayName(event.target.value)}
                  placeholder="Ej. Dra. Ana Lopez"
                  value={displayName}
                />
              </Field>
              <Field label="Especialidad">
                <select
                  className="app-input"
                  onChange={(event) => setProfessionalType(event.target.value)}
                  value={professionalType}
                >
                  <option value="NUTRITIONIST">Nutriologo</option>
                  <option value="PHYSIOTHERAPIST">Fisioterapeuta</option>
                  <option value="CHIROPRACTOR">Quiropractico</option>
                  <option value="WELLNESS">Bienestar</option>
                </select>
              </Field>
              <Field label="Correo">
                <input className="app-input" onChange={(event) => setEmail(event.target.value)} placeholder="correo@ejemplo.com" value={email} />
              </Field>
              <Field label="Telefono">
                <input className="app-input" onChange={(event) => setPhone(event.target.value)} placeholder="+52..." value={phone} />
              </Field>
            </div>
            <button className="form-primary-button mt-4" disabled={saving || !displayName.trim()} type="submit">
              Crear profesional
            </button>
          </form>

          <section className="admin-panel">
            <div className="admin-panel-header">
              <div>
                <p>Directorio</p>
                <h2>Profesionales</h2>
              </div>
              <button className="app-button-secondary" onClick={() => refresh()} type="button">
                Actualizar
              </button>
            </div>
            <div className="mt-4 grid gap-3">
              {professionals.length === 0 ? (
                <p className="admin-muted-text">Aun no hay profesionales registrados para este gimnasio.</p>
              ) : (
                professionals.map((professional) => (
                  <button
                    className={`rounded-lg border p-4 text-left ${
                      selectedProfessionalId === professional.id ? "border-[var(--brand-primary)] bg-[var(--brand-primary-soft)]" : "border-[var(--brand-line)] bg-white"
                    }`}
                    key={professional.id}
                    onClick={() => selectProfessional(professional.id)}
                    type="button"
                  >
                    <strong className="block text-base font-black text-[var(--brand-ink)]">{professional.displayName}</strong>
                    <span className="mt-1 block text-sm text-[var(--brand-muted)]">
                      {professional.professionalType} - {professional.status}
                    </span>
                  </button>
                ))
              )}
            </div>
          </section>
        </aside>

        <section className="grid gap-5">
          <form className="admin-panel" onSubmit={submitService}>
            <div className="admin-panel-header">
              <div>
                <p>Catalogo del profesional</p>
                <h2>Agregar servicio</h2>
              </div>
            </div>
            <div className="mt-4 grid gap-3 md:grid-cols-2">
              <Field label="Nombre del servicio">
                <input className="app-input" onChange={(event) => setServiceName(event.target.value)} placeholder="Consulta nutricional" value={serviceName} />
              </Field>
              <Field label="Tipo">
                <input className="app-input" onChange={(event) => setServiceType(event.target.value)} placeholder="NUTRITION" value={serviceType} />
              </Field>
              <Field label="Duracion en minutos">
                <input className="app-input" min={15} onChange={(event) => setDurationMinutes(Number(event.target.value))} type="number" value={durationMinutes} />
              </Field>
              <Field label="Precio">
                <input className="app-input" min={0} onChange={(event) => setPrice(event.target.value)} step="0.01" type="number" value={price} />
              </Field>
            </div>
            <button className="form-primary-button mt-4" disabled={saving || !selectedProfessionalId || !serviceName.trim()} type="submit">
              Agregar servicio
            </button>
          </form>

          <form className="admin-panel" onSubmit={submitAvailability}>
            <div className="admin-panel-header">
              <div>
                <p>Agenda</p>
                <h2>Agregar disponibilidad</h2>
              </div>
            </div>
            <div className="mt-4 grid gap-3 md:grid-cols-3">
              <Field label="Dia de semana">
                <input className="app-input" max={7} min={1} onChange={(event) => setDayOfWeek(Number(event.target.value))} type="number" value={dayOfWeek} />
              </Field>
              <Field label="Abre">
                <input className="app-input" onChange={(event) => setOpensAt(event.target.value)} type="time" value={opensAt} />
              </Field>
              <Field label="Cierra">
                <input className="app-input" onChange={(event) => setClosesAt(event.target.value)} type="time" value={closesAt} />
              </Field>
            </div>
            <button className="form-primary-button mt-4" disabled={saving || !selectedProfessionalId} type="submit">
              Agregar horario
            </button>
          </form>

          <section className="grid gap-5 md:grid-cols-2">
            <Panel
              title="Servicios registrados"
              items={services.map((service) => `${service.name} - ${formatMoney(service.priceCents, service.currency)} - ${service.durationMinutes} min`)}
            />
            <Panel title="Horarios registrados" items={availability.map((slot) => `Dia ${slot.dayOfWeek}: ${slot.opensAt} - ${slot.closesAt}`)} />
          </section>
        </section>
      </section>
    </AppShell>
  );
}

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <label className="grid gap-2 text-sm font-bold text-[var(--brand-muted)]">
      {label}
      {children}
    </label>
  );
}

function Panel({ title, items }: { title: string; items: string[] }) {
  return (
    <section className="admin-panel">
      <div className="admin-panel-header">
        <div>
          <p>Resumen</p>
          <h2>{title}</h2>
        </div>
      </div>
      <div className="mt-4 grid gap-2 text-sm text-[var(--brand-muted)]">
        {items.length === 0 ? (
          <p>Sin registros.</p>
        ) : (
          items.map((item) => (
            <p className="rounded-lg border border-[var(--brand-line)] bg-white px-3 py-2" key={item}>
              {item}
            </p>
          ))
        )}
      </div>
    </section>
  );
}

function Notice({ tone, text }: { tone: "ok" | "error"; text: string }) {
  const styles = tone === "ok" ? "border-[#bad9c4] bg-[#eff8f1] text-[#31563c]" : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-lg border px-3 py-2 text-sm font-semibold ${styles}`}>{text}</p>;
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}
