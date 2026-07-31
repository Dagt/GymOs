"use client";

import { FormEvent, useEffect, useState } from "react";
import { AuthRequired } from "@/components/AuthRequired";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { normalizePhone, validatePhone } from "@/components/form-validation";
import { localDateTimeToIso, toDateInput } from "@/lib/date-time";
import {
  cancelWellnessAppointment,
  completeWellnessAppointment,
  confirmWellnessAppointment,
  createProfessional,
  createProfessionalAvailability,
  createProfessionalService,
  createWellnessAppointment,
  deleteProfessional,
  getBranches,
  getCurrentOrganization,
  getProfessionalAvailability,
  getProfessionalAvailabilitySlots,
  getProfessionals,
  getProfessionalServices,
  getStoredSession,
  getWellnessAppointments,
  searchClients,
  updateProfessional,
  updateProfessionalService,
  type ClientSummary,
  type ProfessionalServiceSummary,
  type ProfessionalSummary,
  type StoredSession,
  type WellnessAppointment,
  type WellnessAvailabilitySlot,
} from "@/lib/api";
import {
  AppointmentsTabSection,
  CoordinateExternalAppointmentModal,
  DEFAULT_WEEKLY_SCHEDULE,
  CreateAppointmentModal,
  ProfessionalFormModal,
  ProfessionalsDirectorySection,
  SPECIALTY_CONSULTATIONS,
  WellnessNotice,
  WellnessTabs,
  type ProfessionalScheduleDay,
  type WellnessTabKey,
} from "./WellnessSections";

type Organization = Awaited<ReturnType<typeof getCurrentOrganization>>;
type Branch = Awaited<ReturnType<typeof getBranches>>[number];

export default function WellnessPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<Organization | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);

  // Navigation tab
  const [activeTab, setActiveTab] = useState<WellnessTabKey>("agenda");

  // Domain state
  const [professionals, setProfessionals] = useState<ProfessionalSummary[]>([]);
  const [profServicesMap, setProfServicesMap] = useState<Record<string, ProfessionalServiceSummary[]>>({});
  const [appointments, setAppointments] = useState<WellnessAppointment[]>([]);

  // Agenda tab state
  const [todayIso] = useState(() => toDateInput(new Date()));
  const [agendaDate, setAgendaDate] = useState(todayIso);
  const [agendaProfId, setAgendaProfId] = useState("");
  const [agendaStatusFilter, setAgendaStatusFilter] = useState("");
  const [loadingAppointments, setLoadingAppointments] = useState(false);

  // Modal Agendar Cita State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [clientQuery, setClientQuery] = useState("");
  const [clientResults, setClientResults] = useState<ClientSummary[]>([]);
  const [selectedClient, setSelectedClient] = useState<ClientSummary | null>(null);
  const [modalProfId, setModalProfId] = useState("");
  const [modalServices, setModalServices] = useState<ProfessionalServiceSummary[]>([]);
  const [modalServiceId, setModalServiceId] = useState("");
  const [modalDate, setModalDate] = useState(todayIso);
  const [externalRequestedTime, setExternalRequestedTime] = useState("10:00");
  const [modalSlots, setModalSlots] = useState<WellnessAvailabilitySlot[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<WellnessAvailabilitySlot | null>(null);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [notes, setNotes] = useState("");

  // Modal Coordinar Cita Externa State
  const [isCoordModalOpen, setIsCoordModalOpen] = useState(false);
  const [coordinatingAppointment, setCoordinatingAppointment] = useState<WellnessAppointment | null>(null);

  // Modal Crear/Editar Profesional State
  const [isProfModalOpen, setIsProfModalOpen] = useState(false);
  const [editingProfId, setEditingProfId] = useState<string | null>(null);
  const [profDisplayName, setProfDisplayName] = useState("");
  const [profType, setProfType] = useState("NUTRITIONIST");
  const [profOfficeLocation, setProfOfficeLocation] = useState("");
  const [modality, setModality] = useState<"STAFF" | "EXTERNAL">("STAFF");
  const [durationMinutes, setDurationMinutes] = useState(45);
  const [price, setPrice] = useState("500");
  const [scheduleDays, setScheduleDays] = useState<ProfessionalScheduleDay[]>(DEFAULT_WEEKLY_SCHEDULE);
  const [profEmail, setProfEmail] = useState("");
  const [profPhone, setProfPhone] = useState("");
  const [profBio, setProfBio] = useState("");
  const [profStatus, setProfStatus] = useState("ACTIVE");

  // UI status
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);

      try {
        const [loadedOrg, loadedBranches, loadedProfs] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken),
          getProfessionals(stored.accessToken),
        ]);

        if (!active) return;

        setOrganization(loadedOrg);
        setBranches(loadedBranches);
        setProfessionals(loadedProfs);

        // Fetch services map for all professionals
        const servicesMap: Record<string, ProfessionalServiceSummary[]> = {};
        await Promise.all(
          loadedProfs.map(async (p) => {
            const srvs = await getProfessionalServices(stored.accessToken, p.id).catch(() => []);
            servicesMap[p.id] = srvs;
          })
        );
        if (active) setProfServicesMap(servicesMap);

        // Initial appointments load
        await loadAppointments(stored.accessToken, todayIso, "", "");
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Error al cargar módulo de bienestar.");
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
  }, [todayIso]);

  async function reloadDirectory(token: string) {
    const loadedProfs = await getProfessionals(token);
    setProfessionals(loadedProfs);
    const servicesMap: Record<string, ProfessionalServiceSummary[]> = {};
    await Promise.all(
      loadedProfs.map(async (p) => {
        const srvs = await getProfessionalServices(token, p.id).catch(() => []);
        servicesMap[p.id] = srvs;
      })
    );
    setProfServicesMap(servicesMap);
  }

  async function loadAppointments(
    token: string,
    dateStr: string,
    profId: string,
    statusStr: string,
  ) {
    setLoadingAppointments(true);
    try {
      const list = await getWellnessAppointments(token, "", profId, statusStr);
      setAppointments(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al obtener citas.");
    } finally {
      setLoadingAppointments(false);
    }
  }

  // --- Handlers Agenda ---
  const handleDateChange = (newDate: string) => {
    setAgendaDate(newDate);
    if (session) {
      void loadAppointments(session.accessToken, newDate, agendaProfId, agendaStatusFilter);
    }
  };

  const handleProfFilterChange = (profId: string) => {
    setAgendaProfId(profId);
    if (session) {
      void loadAppointments(session.accessToken, agendaDate, profId, agendaStatusFilter);
    }
  };

  const handleStatusFilterChange = (statusStr: string) => {
    setAgendaStatusFilter(statusStr);
    if (session) {
      void loadAppointments(session.accessToken, agendaDate, agendaProfId, statusStr);
    }
  };

  const handleConfirmAppointment = async (id: string) => {
    if (!session) return;
    try {
      setError(null);
      await confirmWellnessAppointment(session.accessToken, id);
      setMessage("Cita confirmada correctamente.");
      await loadAppointments(session.accessToken, agendaDate, agendaProfId, agendaStatusFilter);
    } catch (err) {
      setError(err instanceof Error ? err.message : "No fue posible confirmar la cita.");
    }
  };

  const handleOpenCoordinateModal = (app: WellnessAppointment) => {
    setCoordinatingAppointment(app);
    setIsCoordModalOpen(true);
  };

  const handleConfirmCoordinatedAppointment = async (data: { date: string; time: string; notes: string }) => {
    if (!session || !coordinatingAppointment) return;
    setSaving(true);
    setError(null);
    setMessage(null);

    try {
      const startsAtIso = localDateTimeToIso(data.date, data.time);
      if (!startsAtIso) {
        setError("La fecha u hora acordada no es válida.");
        return;
      }
      await confirmWellnessAppointment(session.accessToken, coordinatingAppointment.id, {
        startsAt: startsAtIso,
        notes: data.notes,
      });
      setMessage(`Cita para ${coordinatingAppointment.clientFullName} coordinada y confirmada exitosamente.`);
      setIsCoordModalOpen(false);
      setCoordinatingAppointment(null);
      await loadAppointments(session.accessToken, agendaDate, agendaProfId, agendaStatusFilter);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al coordinar la cita.");
    } finally {
      setSaving(false);
    }
  };

  const handleCompleteAppointment = async (id: string) => {
    if (!session) return;
    try {
      setError(null);
      await completeWellnessAppointment(session.accessToken, id);
      setMessage("Cita marcada como completada.");
      await loadAppointments(session.accessToken, agendaDate, agendaProfId, agendaStatusFilter);
    } catch (err) {
      setError(err instanceof Error ? err.message : "No fue posible completar la cita.");
    }
  };

  const handleCancelAppointment = async (id: string) => {
    if (!session) return;
    try {
      setError(null);
      await cancelWellnessAppointment(session.accessToken, id, "Cancelado desde panel de administración");
      setMessage("Cita cancelada.");
      await loadAppointments(session.accessToken, agendaDate, agendaProfId, agendaStatusFilter);
    } catch (err) {
      setError(err instanceof Error ? err.message : "No fue posible cancelar la cita.");
    }
  };

  // --- Handlers Modal Agendar Cita ---
  const handleOpenModal = () => {
    setIsModalOpen(true);
    setClientQuery("");
    setClientResults([]);
    setSelectedClient(null);
    const initialProfId = professionals[0]?.id || "";
    setModalProfId(initialProfId);
    setSelectedSlot(null);
    setExternalRequestedTime("10:00");
    setNotes("");
    setModalDate(agendaDate); // Pre-fills current calendar date!
    if (initialProfId && session) {
      void loadModalServicesAndSlots(session.accessToken, initialProfId, "", agendaDate);
    }
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
  };

  const handleClientQueryChange = async (query: string) => {
    setClientQuery(query);
    if (!session || query.trim().length < 2) {
      setClientResults([]);
      return;
    }
    try {
      const results = await searchClients(session.accessToken, query);
      setClientResults(results);
    } catch {
      setClientResults([]);
    }
  };

  const handleModalProfChange = async (profId: string) => {
    setModalProfId(profId);
    setSelectedSlot(null);
    if (session && profId) {
      await loadModalServicesAndSlots(session.accessToken, profId, "", modalDate);
    }
  };

  const handleModalServiceChange = async (srvId: string) => {
    setModalServiceId(srvId);
    setSelectedSlot(null);
    if (session && modalProfId) {
      await loadModalServicesAndSlots(session.accessToken, modalProfId, srvId, modalDate);
    }
  };

  const handleModalDateChange = async (dStr: string) => {
    setModalDate(dStr);
    setSelectedSlot(null);
    if (session && modalProfId) {
      await loadModalServicesAndSlots(session.accessToken, modalProfId, modalServiceId, dStr);
    }
  };

  async function loadModalServicesAndSlots(
    token: string,
    profId: string,
    srvId: string,
    dateStr: string,
  ) {
    setLoadingSlots(true);
    try {
      const srvList = await getProfessionalServices(token, profId);
      const resolvedServiceId = srvId || srvList[0]?.id || "";
      setModalServices(srvList);
      setModalServiceId(resolvedServiceId);

      const professional = professionals.find((candidate) => candidate.id === profId);
      if (professional?.engagementType === "EXTERNAL") {
        setModalSlots([]);
        return;
      }

      const slotList = await getProfessionalAvailabilitySlots(
        token,
        profId,
        resolvedServiceId || undefined,
        dateStr,
      );
      setModalSlots(slotList);
    } catch {
      setModalSlots([]);
    } finally {
      setLoadingSlots(false);
    }
  }

  const handleSubmitModalAppointment = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!session || !selectedClient || !modalProfId) return;

    const modalProf = professionals.find((p) => p.id === modalProfId);
    const isExternalProf = modalProf?.engagementType === "EXTERNAL";

    if (isExternalProf && !externalRequestedTime) {
      setError("Debes indicar una hora solicitada para la cita externa.");
      return;
    }

    if (!selectedSlot && !isExternalProf) {
      setError("Debes seleccionar un horario para la cita.");
      return;
    }

    const startsAtIso = selectedSlot?.startsAt
      ?? localDateTimeToIso(modalDate, externalRequestedTime);
    if (!startsAtIso) {
      setError("La fecha u hora solicitada no es válida.");
      return;
    }

    setSaving(true);
    setError(null);
    setMessage(null);

    try {
      const finalSrvId = modalServiceId || modalServices[0]?.id || undefined;
      await createWellnessAppointment(session.accessToken, {
        professionalId: modalProfId,
        serviceId: finalSrvId,
        clientId: selectedClient.id,
        startsAt: startsAtIso,
        notes: notes || undefined,
      });
      setMessage(
        isExternalProf
          ? `Solicitud registrada para ${selectedClient.firstName} ${selectedClient.lastName}. El horario queda pendiente de confirmación del especialista.`
          : `Cita agendada y confirmada para ${selectedClient.firstName} ${selectedClient.lastName}.`
      );
      setIsModalOpen(false);
      await loadAppointments(session.accessToken, agendaDate, agendaProfId, agendaStatusFilter);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al crear la cita.");
    } finally {
      setSaving(false);
    }
  };

  // --- Handlers Equipo Profesional ---
  const handleOpenCreateProfModal = () => {
    handleCancelProfEdit();
    setIsProfModalOpen(true);
  };

  const handleUpdateScheduleDay = (dayOfWeek: number, patch: Partial<ProfessionalScheduleDay>) => {
    setScheduleDays((prev) =>
      prev.map((d) => (d.dayOfWeek === dayOfWeek ? { ...d, ...patch } : d))
    );
  };

  const handleEditProf = (p: ProfessionalSummary) => {
    setEditingProfId(p.id);
    setProfDisplayName(p.displayName);
    setProfType(p.professionalType);
    setProfOfficeLocation(p.officeLocation || "");
    const isDePlanta = p.engagementType === "STAFF";
    setModality(p.engagementType);
    setProfEmail(p.email || "");
    setProfPhone(p.phone || "");
    setProfBio(p.bio || "");
    setProfStatus(p.status || "ACTIVE");

    // Populate duration and price from their existing consultation service
    const existingSrvs = profServicesMap[p.id] || [];
    if (existingSrvs.length > 0) {
      setDurationMinutes(existingSrvs[0].durationMinutes);
      setPrice((existingSrvs[0].priceCents / 100).toString());
    } else {
      const info = SPECIALTY_CONSULTATIONS[p.professionalType] || SPECIALTY_CONSULTATIONS.NUTRITIONIST;
      setDurationMinutes(info.defaultDuration);
      setPrice(info.defaultPrice);
    }

    if (session && p.id && isDePlanta) {
      getProfessionalAvailability(session.accessToken, p.id)
        .then((avail) => {
          setScheduleDays(
            DEFAULT_WEEKLY_SCHEDULE.map((day) => {
              const match = avail.find((a) => a.dayOfWeek === day.dayOfWeek);
              if (match) {
                return { ...day, enabled: true, opensAt: match.opensAt, closesAt: match.closesAt };
              }
              return { ...day, enabled: false };
            })
          );
        })
        .catch(() => null);
    }

    setIsProfModalOpen(true);
  };

  const handleCancelProfEdit = () => {
    setEditingProfId(null);
    setProfDisplayName("");
    setProfOfficeLocation("");
    setModality("STAFF");
    setScheduleDays(DEFAULT_WEEKLY_SCHEDULE);
    const defaultInfo = SPECIALTY_CONSULTATIONS.NUTRITIONIST;
    setDurationMinutes(defaultInfo.defaultDuration);
    setPrice(defaultInfo.defaultPrice);
    setProfEmail("");
    setProfPhone("");
    setProfBio("");
    setProfStatus("ACTIVE");
    setIsProfModalOpen(false);
  };

  const handleDeleteProf = async (profId: string) => {
    if (!session) return;
    setSaving(true);
    setError(null);
    setMessage(null);

    try {
      await deleteProfessional(session.accessToken, profId);
      setMessage("Profesional y sus citas asociadas fueron eliminados correctamente.");
      await reloadDirectory(session.accessToken);
      await loadAppointments(session.accessToken, agendaDate, agendaProfId, agendaStatusFilter);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al eliminar profesional.");
    } finally {
      setSaving(false);
    }
  };

  const handleSubmitProf = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!session) return;

    if (profPhone.trim()) {
      const phoneErr = validatePhone(profPhone, false);
      if (phoneErr) {
        setError(phoneErr);
        return;
      }
    }

    setSaving(true);
    setError(null);
    setMessage(null);

    const formattedPhone = profPhone.trim() ? normalizePhone(profPhone) : undefined;
    const finalOfficeLoc = profOfficeLocation.trim()
      ? profOfficeLocation.trim()
      : modality === "STAFF"
        ? "Consultorio Sucursal"
        : undefined;

    try {
      let savedProfId = editingProfId;

      if (editingProfId) {
        await updateProfessional(session.accessToken, editingProfId, {
          displayName: profDisplayName,
          professionalType: profType,
          officeLocation: finalOfficeLoc,
          engagementType: modality,
          email: profEmail || undefined,
          phone: formattedPhone,
          bio: profBio || undefined,
          status: profStatus,
        });
        setMessage("Profesional actualizado correctamente.");
      } else {
        const created = await createProfessional(session.accessToken, {
          displayName: profDisplayName,
          professionalType: profType,
          officeLocation: finalOfficeLoc,
          engagementType: modality,
          email: profEmail || undefined,
          phone: formattedPhone,
          bio: profBio || undefined,
        });
        savedProfId = created.id;
        setMessage("Profesional registrado exitosamente.");
      }

      // Auto-create / update consultation service for this professional
      if (savedProfId) {
        const consultation = SPECIALTY_CONSULTATIONS[profType] || SPECIALTY_CONSULTATIONS.NUTRITIONIST;
        const priceCents = Math.round(Number(price) * 100);
        const existingServices = await getProfessionalServices(session.accessToken, savedProfId).catch(() => []);
        if (existingServices.length > 0) {
          await updateProfessionalService(
            session.accessToken,
            savedProfId,
            existingServices[0].id,
            {
              name: consultation.serviceName,
              serviceType: consultation.serviceType,
              durationMinutes: durationMinutes || consultation.defaultDuration,
              priceCents: priceCents,
              currency: "MXN",
              status: "ACTIVE",
            }
          ).catch(() => null);
        } else {
          await createProfessionalService(
            session.accessToken,
            savedProfId,
            {
              name: consultation.serviceName,
              serviceType: consultation.serviceType,
              durationMinutes: durationMinutes || consultation.defaultDuration,
              priceCents: priceCents,
              currency: "MXN",
            }
          ).catch(() => null);
        }
      }

      // Si es De Planta, guardamos su horario semanal configurado
      if (modality === "STAFF" && savedProfId) {
        const enabledDays = scheduleDays.filter((d) => d.enabled);
        await Promise.all(
          enabledDays.map((d) =>
            createProfessionalAvailability(session.accessToken, savedProfId!, {
              dayOfWeek: d.dayOfWeek,
              opensAt: d.opensAt,
              closesAt: d.closesAt,
            }).catch(() => null)
          )
        );
      }

      handleCancelProfEdit();
      await reloadDirectory(session.accessToken);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al guardar profesional.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <LoadingScreen text="Cargando módulo de bienestar..." />;
  }

  if (!session) {
    return <AuthRequired title="Inicia sesión para administrar servicios de bienestar" />;
  }

  const activeBranch = branches.find((branch) => branch.id === session.user.branchId) ?? null;
  const requestedCount = appointments.filter((a) => a.status === "REQUESTED").length;

  const canManageProfessionals = Boolean(
    session.user.permissions?.includes("professional.manage") ||
    session.user.permissions?.includes("platform.admin") ||
    session.user.roles?.some((r) =>
      r.toLowerCase().includes("dueño") ||
      r.toLowerCase().includes("dueno") ||
      r.toLowerCase().includes("gerente") ||
      r.toLowerCase().includes("admin")
    ) ||
    session.user.console === "PLATFORM_OWNER" ||
    session.user.console === "TENANT_OWNER" ||
    session.user.console === "TENANT_MANAGER"
  );

  return (
    <AppShell
      branchName={activeBranch?.name ?? null}
      eyebrow="Servicios"
      mode={activeBranch ? "operation" : "setup"}
      organizationName={organization?.tradeName ?? null}
      session={session}
      subtitle="Especialistas, modalidad, honorarios y agendamiento de citas."
      title="Servicios de Bienestar"
    >
      {error ? <InlineError message={error} /> : null}
      {message ? <WellnessNotice text={message} tone="ok" /> : null}

      <div className="mx-auto w-full max-w-7xl space-y-4">
        <WellnessTabs
          activeTab={activeTab}
          onTabChange={setActiveTab}
          requestedCount={requestedCount}
        />

        {activeTab === "agenda" && (
          <AppointmentsTabSection
            appointments={appointments}
            date={agendaDate}
            loadingAppointments={loadingAppointments}
            onCancel={handleCancelAppointment}
            onComplete={handleCompleteAppointment}
            onConfirm={handleConfirmAppointment}
            onCoordinate={handleOpenCoordinateModal}
            onDateChange={handleDateChange}
            onOpenNewModal={handleOpenModal}
            onProfessionalChange={handleProfFilterChange}
            onStatusFilterChange={handleStatusFilterChange}
            professionals={professionals}
            selectedProfessionalId={agendaProfId}
            statusFilter={agendaStatusFilter}
          />
        )}

        {activeTab === "professionals" && (
          <ProfessionalsDirectorySection
            canManageProfessionals={canManageProfessionals}
            onDeleteProfessional={handleDeleteProf}
            onEditProfessional={handleEditProf}
            onOpenCreateModal={handleOpenCreateProfModal}
            professionalServicesMap={profServicesMap}
            professionals={professionals}
          />
        )}
      </div>

      <CreateAppointmentModal
        appointmentDate={modalDate}
        clientQuery={clientQuery}
        clientResults={clientResults}
        externalRequestedTime={externalRequestedTime}
        loadingSlots={loadingSlots}
        notes={notes}
        onAppointmentDateChange={handleModalDateChange}
        onClientQueryChange={handleClientQueryChange}
        onClose={handleCloseModal}
        onExternalRequestedTimeChange={setExternalRequestedTime}
        onNotesChange={setNotes}
        onSelectClient={setSelectedClient}
        onSelectProfessional={handleModalProfChange}
        onSelectService={handleModalServiceChange}
        onSelectSlot={setSelectedSlot}
        onSubmit={handleSubmitModalAppointment}
        open={isModalOpen}
        professionals={professionals}
        saving={saving}
        selectedClient={selectedClient}
        selectedProfessionalId={modalProfId}
        selectedServiceId={modalServiceId}
        selectedSlot={selectedSlot}
        services={modalServices}
        slots={modalSlots}
      />

      <CoordinateExternalAppointmentModal
        appointment={coordinatingAppointment}
        onClose={() => setIsCoordModalOpen(false)}
        onSubmit={handleConfirmCoordinatedAppointment}
        open={isCoordModalOpen}
        saving={saving}
      />

      <ProfessionalFormModal
        bio={profBio}
        displayName={profDisplayName}
        durationMinutes={durationMinutes}
        editingProfessionalId={editingProfId}
        email={profEmail}
        modality={modality}
        officeLocation={profOfficeLocation}
        onBioChange={setProfBio}
        onClose={handleCancelProfEdit}
        onDisplayNameChange={setProfDisplayName}
        onDurationChange={setDurationMinutes}
        onEmailChange={setProfEmail}
        onModalityChange={setModality}
        onOfficeLocationChange={setProfOfficeLocation}
        onPhoneChange={setProfPhone}
        onPriceChange={setPrice}
        onProfessionalTypeChange={setProfType}
        onStatusChange={setProfStatus}
        onSubmit={handleSubmitProf}
        onUpdateScheduleDay={handleUpdateScheduleDay}
        open={isProfModalOpen}
        phone={profPhone}
        price={price}
        professionalType={profType}
        saving={saving}
        scheduleDays={scheduleDays}
        status={profStatus}
      />
    </AppShell>
  );
}
