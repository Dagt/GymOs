import { type FormEvent, useEffect, useMemo, useState } from "react";
import {
  createAccessDevice,
  createTemporaryPass,
  getAccessDevices,
  getAccessLogs,
  getDeviceRequiredBenefits,
  getPhysicalClientState,
  getPhysicalIntegration,
  getPhysicalPersonCandidates,
  getStoredSession,
  issuePhysicalConnectorActivation,
  linkPhysicalClient,
  linkPhysicalDevice,
  manualOpenAccess,
  matchPhysicalPersonCandidate,
  queuePhysicalClientSynchronization,
  reconcilePhysicalIntegration,
  rejectPhysicalPersonCandidate,
  replaceDeviceRequiredBenefits,
  requestAccess,
  revokePhysicalConnector,
  searchClients,
  unlinkPhysicalDevice,
  updatePhysicalIntegration,
  type AccessDecision,
  type AccessDevice,
  type AccessDevicePayload,
  type AccessLog,
  type ClientSummary,
  type PhysicalClientState,
  type PhysicalConnectorActivationCode,
  type PhysicalIntegration,
  type PhysicalPersonCandidate,
  type StoredSession,
} from "@/lib/api";

export function useAccessController() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [devices, setDevices] = useState<AccessDevice[]>([]);
  const [logs, setLogs] = useState<AccessLog[]>([]);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [selectedClientId, setSelectedClientId] = useState("");
  const [query, setQuery] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [deviceCode, setDeviceCode] = useState("");
  const [deviceBenefitIdsByDevice, setDeviceBenefitIdsByDevice] = useState<
    Record<string, string[]>
  >({});
  const [manualReason, setManualReason] = useState("");
  const [temporaryReason, setTemporaryReason] = useState("");
  const [temporaryHours, setTemporaryHours] = useState(4);
  const [temporaryMaxUses, setTemporaryMaxUses] = useState(1);
  const [physicalIntegration, setPhysicalIntegration] =
    useState<PhysicalIntegration | null>(null);
  const [physicalClientState, setPhysicalClientState] =
    useState<PhysicalClientState | null>(null);
  const [physicalConnectionName, setPhysicalConnectionName] = useState("");
  const [physicalConnectorKey, setPhysicalConnectorKey] = useState("");
  const [physicalProviderKey, setPhysicalProviderKey] = useState("");
  const [physicalSyncEnabled, setPhysicalSyncEnabled] = useState(false);
  const [physicalMappingDrafts, setPhysicalMappingDrafts] = useState<
    Record<string, { reference: string; name: string }>
  >({});
  const [externalPersonRef, setExternalPersonRef] = useState("");
  const [externalDeviceRef, setExternalDeviceRef] = useState("");
  const [externalDeviceName, setExternalDeviceName] = useState("");
  const [connectorActivation, setConnectorActivation] =
    useState<PhysicalConnectorActivationCode | null>(null);
  const [physicalCandidates, setPhysicalCandidates] = useState<
    PhysicalPersonCandidate[]
  >([]);
  const [lastDecision, setLastDecision] = useState<AccessDecision | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

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
        const [
          loadedDevices,
          loadedLogs,
          loadedClients,
          loadedPhysicalIntegration,
        ] = await Promise.all([
          getAccessDevices(stored.accessToken),
          getAccessLogs(stored.accessToken),
          searchClients(stored.accessToken, "", "ACTIVE"),
          getPhysicalIntegration(stored.accessToken),
        ]);
        const loadedBenefitEntries = await Promise.all(
          loadedDevices.map(async (device) => [
            device.id,
            await getDeviceRequiredBenefits(stored.accessToken, device.id),
          ] as const),
        );
        if (!active) {
          return;
        }
        setDevices(loadedDevices);
        setDeviceBenefitIdsByDevice(Object.fromEntries(loadedBenefitEntries));
        setLogs(loadedLogs);
        setClients(loadedClients);
        const initialDevice = loadedDevices.find(
          (device) => device.status === "ACTIVE",
        );
        setDeviceCode(initialDevice?.deviceCode ?? "");
        const initialDeviceLink = loadedPhysicalIntegration.deviceLinks.find(
          (candidate) => candidate.accessDeviceId === initialDevice?.id,
        );
        setExternalDeviceRef(initialDeviceLink?.externalDeviceRef ?? "");
        setExternalDeviceName(initialDeviceLink?.externalDeviceName ?? "");
        hydratePhysicalIntegration(loadedPhysicalIntegration);
        if (
          hasPermission(stored, "access.physical.manage") &&
          loadedPhysicalIntegration.connection.id
        ) {
          setPhysicalCandidates(
            await getPhysicalPersonCandidates(stored.accessToken),
          );
        }
      } catch (exception) {
        if (active) {
          setError(
            exception instanceof Error
              ? exception.message
              : "No fue posible cargar accesos.",
          );
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

  const selectedClient = useMemo(
    () => clients.find((client) => client.id === selectedClientId) ?? null,
    [clients, selectedClientId],
  );

  const selectedDevice = useMemo(
    () => devices.find((device) => device.deviceCode === deviceCode) ?? null,
    [deviceCode, devices],
  );

  const deviceRequiredBenefitIds = useMemo(
    () =>
      selectedDevice ? (deviceBenefitIdsByDevice[selectedDevice.id] ?? []) : [],
    [deviceBenefitIdsByDevice, selectedDevice],
  );

  useEffect(() => {
    if (!session || !selectedClientId) {
      return;
    }
    let active = true;
    const accessToken = session.accessToken;
    async function loadPhysicalClient() {
      try {
        const state = await getPhysicalClientState(
          accessToken,
          selectedClientId,
        );
        if (active) {
          setPhysicalClientState(state);
          setExternalPersonRef(state.externalPersonRef ?? "");
        }
      } catch {
        if (active) {
          setPhysicalClientState(null);
        }
      }
    }
    void loadPhysicalClient();
    return () => {
      active = false;
    };
  }, [session, selectedClientId]);

  async function refreshLogs(activeSession = session) {
    if (!activeSession) {
      return;
    }
    setLogs(await getAccessLogs(activeSession.accessToken));
  }

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await searchClients(session.accessToken, query, "ACTIVE");
      setClients(result);
      if (result.length === 1) {
        selectClient(result[0]);
      }
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible buscar clientes.",
      );
    } finally {
      setBusy(false);
    }
  }

  function selectClient(client: ClientSummary) {
    setSelectedClientId(client.id);
    setIdentifier(client.email ?? client.phone ?? "");
    setPhysicalClientState(null);
    setExternalPersonRef("");
  }

  function selectDeviceCode(value: string) {
    setDeviceCode(value);
    const selected = devices.find((device) => device.deviceCode === value);
    const link = physicalIntegration?.deviceLinks.find(
      (candidate) => candidate.accessDeviceId === selected?.id,
    );
    setExternalDeviceRef(link?.externalDeviceRef ?? "");
    setExternalDeviceName(link?.externalDeviceName ?? "");
  }

  async function evaluateAccess() {
    if (!session) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const decision = await requestAccess(session.accessToken, {
        clientId: selectedClientId || null,
        identifier,
        deviceCode,
        idempotencyKey: crypto.randomUUID(),
      });
      setLastDecision(decision);
      setMessage(
        decision.decision === "GRANTED"
          ? "Acceso concedido."
          : "Acceso denegado.",
      );
      await refreshLogs(session);
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible evaluar acceso.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function openManually() {
    if (!session || !manualReason.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const decision = await manualOpenAccess(session.accessToken, {
        clientId: selectedClientId || null,
        deviceCode,
        reason: manualReason,
      });
      setLastDecision(decision);
      setManualReason("");
      setMessage("Apertura manual registrada.");
      await refreshLogs(session);
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible abrir manualmente.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function createPass() {
    if (!session || !selectedClientId || !temporaryReason.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const expiresAt = new Date(
        Date.now() + temporaryHours * 60 * 60 * 1000,
      ).toISOString();
      const pass = await createTemporaryPass(session.accessToken, {
        clientId: selectedClientId,
        expiresAt,
        maxUses: temporaryMaxUses,
        reason: temporaryReason,
      });
      setTemporaryReason("");
      setMessage(`Autorización temporal creada para ${pass.clientName}.`);
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible crear la autorización temporal.",
      );
    } finally {
      setBusy(false);
    }
  }

  function hydratePhysicalIntegration(integration: PhysicalIntegration) {
    const mappedByBenefit = new Map(
      integration.mappings.map((mapping) => [mapping.benefitId, mapping]),
    );
    setPhysicalIntegration(integration);
    setPhysicalConnectionName(integration.connection.displayName);
    setPhysicalConnectorKey(integration.connection.connectorKey ?? "");
    setPhysicalProviderKey(integration.connection.providerKey || "");
    setPhysicalSyncEnabled(integration.connection.enabled);
    setPhysicalMappingDrafts(
      Object.fromEntries(
        integration.availableBenefits.map((benefit) => {
          const mapping = mappedByBenefit.get(benefit.id);
          return [
            benefit.id,
            {
              reference: mapping?.externalAccessLevelRef ?? "",
              name: mapping?.externalAccessLevelName ?? "",
            },
          ];
        }),
      ),
    );
  }

  function setPhysicalMapping(
    benefitId: string,
    field: "reference" | "name",
    value: string,
  ) {
    setPhysicalMappingDrafts((current) => ({
      ...current,
      [benefitId]: {
        reference: current[benefitId]?.reference ?? "",
        name: current[benefitId]?.name ?? "",
        [field]: value,
      },
    }));
  }

  async function savePhysicalIntegration() {
    if (!session || !physicalIntegration) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const integration = await updatePhysicalIntegration(session.accessToken, {
        enabled: physicalSyncEnabled,
        providerKey: physicalProviderKey,
        displayName: physicalConnectionName,
        connectorKey: physicalConnectorKey || null,
        benefitMappings: physicalIntegration.availableBenefits.flatMap(
          (benefit) => {
            const draft = physicalMappingDrafts[benefit.id];
            if (!draft?.reference.trim()) {
              return [];
            }
            return [
              {
                benefitId: benefit.id,
                externalAccessLevelRef: draft.reference.trim(),
                externalAccessLevelName: draft.name.trim() || null,
              },
            ];
          },
        ),
      });
      hydratePhysicalIntegration(integration);
      setMessage(
        "Configuración de acceso físico guardada. No se almacenaron secretos del proveedor.",
      );
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible guardar la configuración física.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function saveDeviceRequiredBenefits(benefitIds: string[]) {
    if (!session || !selectedDevice) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const savedBenefitIds = await replaceDeviceRequiredBenefits(
        session.accessToken,
        selectedDevice.id,
        benefitIds,
      );
      setDeviceBenefitIdsByDevice((current) => ({
        ...current,
        [selectedDevice.id]: savedBenefitIds,
      }));
      setMessage(
        savedBenefitIds.length > 0
          ? `Requisitos de ${selectedDevice.name} actualizados.`
          : `${selectedDevice.name} quedó sin requisitos de acceso. Revísalo antes de operar físicamente.`,
      );
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible guardar los requisitos del punto de acceso.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function createDevice(payload: AccessDevicePayload) {
    if (!session) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const device = await createAccessDevice(session.accessToken, payload);
      setDevices((current) =>
        [...current, device].sort((left, right) =>
          left.name.localeCompare(right.name, "es-MX"),
        ),
      );
      setDeviceCode(device.deviceCode);
      setExternalDeviceRef("");
      setExternalDeviceName("");
      setDeviceBenefitIdsByDevice((current) => ({
        ...current,
        [device.id]: payload.requiredBenefitId
          ? [payload.requiredBenefitId]
          : [],
      }));
      setMessage(
        `${device.name} quedó registrado como punto de acceso activo.`,
      );
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible registrar el punto de acceso.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function linkSelectedPhysicalClient() {
    if (!session || !selectedClientId || !externalPersonRef.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const state = await linkPhysicalClient(
        session.accessToken,
        selectedClientId,
        externalPersonRef.trim(),
      );
      setPhysicalClientState(state);
      setExternalPersonRef(state.externalPersonRef ?? "");
      setMessage("Vínculo con la persona del proveedor guardado.");
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible vincular al socio con el proveedor físico.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function queueSelectedPhysicalSynchronization() {
    if (!session || !selectedClientId) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const state = await queuePhysicalClientSynchronization(
        session.accessToken,
        selectedClientId,
      );
      setPhysicalClientState(state);
      setMessage(
        state.syncState?.status === "BLOCKED"
          ? "La sincronización quedó bloqueada hasta completar el mapeo de beneficios."
          : "Sincronización preparada para el conector local.",
      );
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible preparar la sincronización.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function refreshPhysicalCandidates() {
    if (!session || !hasPermission(session, "access.physical.manage")) return;
    setPhysicalCandidates(
      await getPhysicalPersonCandidates(session.accessToken),
    );
  }

  async function issueConnectorActivation() {
    if (!session || !physicalIntegration?.connection.id) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const purpose = physicalIntegration.connection.credentialActive
        ? "ROTATION"
        : "ACTIVATION";
      const activation = await issuePhysicalConnectorActivation(
        session.accessToken,
        purpose,
      );
      setConnectorActivation(activation);
      setMessage(
        "Código de activación generado. Cópialo ahora al GymFort Local Connector.",
      );
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible generar la activación del Connector.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function revokeConnector() {
    if (!session || !physicalIntegration?.connection.id) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await revokePhysicalConnector(session.accessToken);
      setConnectorActivation(null);
      hydratePhysicalIntegration(
        await getPhysicalIntegration(session.accessToken),
      );
      setMessage("Credencial del GymFort Local Connector revocada.");
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible revocar el Connector.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function reconcileAllPhysicalClients() {
    if (!session) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await reconcilePhysicalIntegration(session.accessToken);
      const integration = await getPhysicalIntegration(session.accessToken);
      hydratePhysicalIntegration(integration);
      if (selectedClientId) {
        setPhysicalClientState(
          await getPhysicalClientState(session.accessToken, selectedClientId),
        );
      }
      setMessage(
        `Reconciliación solicitada para ${result.queuedClients} socio(s).`,
      );
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible reconciliar el estado físico.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function savePhysicalDeviceLink() {
    if (!session || !selectedDevice || !externalDeviceRef.trim()) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await linkPhysicalDevice(
        session.accessToken,
        selectedDevice.id,
        externalDeviceRef.trim(),
        externalDeviceName.trim() || null,
      );
      hydratePhysicalIntegration(
        await getPhysicalIntegration(session.accessToken),
      );
      setMessage(`${selectedDevice.name} quedó vinculado al recurso externo.`);
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible vincular el recurso físico.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function removePhysicalDeviceLink() {
    if (!session || !selectedDevice) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await unlinkPhysicalDevice(session.accessToken, selectedDevice.id);
      setExternalDeviceRef("");
      setExternalDeviceName("");
      hydratePhysicalIntegration(
        await getPhysicalIntegration(session.accessToken),
      );
      setMessage(`${selectedDevice.name} quedó sin recurso externo.`);
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible quitar el vínculo físico.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function matchCandidate(candidateId: string) {
    if (!session || !selectedClientId) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await matchPhysicalPersonCandidate(
        session.accessToken,
        candidateId,
        selectedClientId,
      );
      await refreshPhysicalCandidates();
      setPhysicalClientState(
        await getPhysicalClientState(session.accessToken, selectedClientId),
      );
      setMessage("Persona externa vinculada al socio seleccionado.");
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible aceptar la coincidencia.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function rejectCandidate(candidateId: string) {
    if (!session) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await rejectPhysicalPersonCandidate(session.accessToken, candidateId);
      await refreshPhysicalCandidates();
      setMessage("Candidato externo descartado.");
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible descartar el candidato.",
      );
    } finally {
      setBusy(false);
    }
  }

  return {
    busy,
    clients,
    createDevice,
    deviceCode,
    deviceBenefitIdsByDevice,
    deviceRequiredBenefitIds,
    devices,
    error,
    externalDeviceName,
    externalDeviceRef,
    identifier,
    lastDecision,
    loading,
    logs,
    manualReason,
    message,
    physicalClientState,
    physicalCandidates,
    physicalConnectionName,
    physicalConnectorKey,
    physicalProviderKey,
    physicalIntegration,
    physicalMappingDrafts,
    physicalSyncEnabled,
    query,
    selectedClient,
    selectedClientId,
    selectedDevice,
    session,
    canManagePhysical: hasPermission(session, "access.physical.manage"),
    canManualOpen: hasPermission(session, "access.manual.open"),
    connectorActivation,
    externalPersonRef,
    temporaryHours,
    temporaryMaxUses,
    temporaryReason,
    createPass,
    evaluateAccess,
    linkSelectedPhysicalClient,
    issueConnectorActivation,
    matchCandidate,
    openManually,
    queueSelectedPhysicalSynchronization,
    reconcileAllPhysicalClients,
    rejectCandidate,
    removePhysicalDeviceLink,
    revokeConnector,
    refreshLogs,
    savePhysicalIntegration,
    savePhysicalDeviceLink,
    saveDeviceRequiredBenefits,
    search,
    selectClient,
    setDeviceCode: selectDeviceCode,
    setIdentifier,
    setManualReason,
    setExternalPersonRef,
    setExternalDeviceName,
    setExternalDeviceRef,
    setPhysicalConnectionName,
    setPhysicalConnectorKey,
    setPhysicalProviderKey,
    setPhysicalMapping,
    setPhysicalSyncEnabled,
    setQuery,
    setTemporaryHours,
    setTemporaryMaxUses,
    setTemporaryReason,
  };
}

function hasPermission(session: StoredSession | null, permission: string) {
  return Boolean(
    session &&
      (session.user.platformAdmin ||
        session.user.permissions.includes("platform.admin") ||
        session.user.permissions.includes(permission)),
  );
}
