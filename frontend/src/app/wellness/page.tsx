"use client";

import { FormEvent, useEffect, useState } from "react";
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
import {
  ProfessionalAvailabilityForm,
  ProfessionalForm,
  ProfessionalsDirectory,
  ProfessionalServiceForm,
  WellnessNotice,
  WellnessSummaryPanels,
} from "./WellnessSections";

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
          await loadProfessionalDetails(stored.accessToken, firstId, () => active);
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

  async function loadProfessionalDetails(
    accessToken: string,
    professionalId: string,
    shouldUpdate = () => true,
  ) {
    const [loadedServices, loadedAvailability] = await Promise.all([
      getProfessionalServices(accessToken, professionalId),
      getProfessionalAvailability(accessToken, professionalId),
    ]);
    if (shouldUpdate()) {
      setServices(loadedServices);
      setAvailability(loadedAvailability);
    }
  }

  async function refresh(activeSession = session, professionalId = selectedProfessionalId) {
    if (!activeSession) {
      return;
    }
    const loaded = await getProfessionals(activeSession.accessToken);
    setProfessionals(loaded);
    const nextId = professionalId || loaded[0]?.id || "";
    setSelectedProfessionalId(nextId);
    if (nextId) {
      await loadProfessionalDetails(activeSession.accessToken, nextId);
    } else {
      setServices([]);
      setAvailability([]);
    }
  }

  async function selectProfessional(professionalId: string) {
    setSelectedProfessionalId(professionalId);
    if (session) {
      await loadProfessionalDetails(session.accessToken, professionalId);
    }
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
      {message ? <WellnessNotice tone="ok" text={message} /> : null}

      <section className="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
        <aside className="grid gap-5">
          <ProfessionalForm
            displayName={displayName}
            email={email}
            onDisplayNameChange={setDisplayName}
            onEmailChange={setEmail}
            onPhoneChange={setPhone}
            onProfessionalTypeChange={setProfessionalType}
            onSubmit={submitProfessional}
            phone={phone}
            professionalType={professionalType}
            saving={saving}
          />
          <ProfessionalsDirectory
            onRefresh={() => refresh()}
            onSelectProfessional={selectProfessional}
            professionals={professionals}
            selectedProfessionalId={selectedProfessionalId}
          />
        </aside>

        <section className="grid gap-5">
          <ProfessionalServiceForm
            durationMinutes={durationMinutes}
            onDurationChange={setDurationMinutes}
            onPriceChange={setPrice}
            onServiceNameChange={setServiceName}
            onServiceTypeChange={setServiceType}
            onSubmit={submitService}
            price={price}
            saving={saving}
            selectedProfessionalId={selectedProfessionalId}
            serviceName={serviceName}
            serviceType={serviceType}
          />
          <ProfessionalAvailabilityForm
            closesAt={closesAt}
            dayOfWeek={dayOfWeek}
            onClosesAtChange={setClosesAt}
            onDayOfWeekChange={setDayOfWeek}
            onOpensAtChange={setOpensAt}
            onSubmit={submitAvailability}
            opensAt={opensAt}
            saving={saving}
            selectedProfessionalId={selectedProfessionalId}
          />
          <WellnessSummaryPanels availability={availability} services={services} />
        </section>
      </section>
    </AppShell>
  );
}
