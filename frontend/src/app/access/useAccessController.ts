import { type FormEvent, useEffect, useMemo, useState } from "react";
import {
  createTemporaryPass,
  getAccessDevices,
  getAccessLogs,
  getPhysicalClientState,
  getPhysicalIntegration,
  getStoredSession,
  linkPhysicalClient,
  manualOpenAccess,
  queuePhysicalClientSynchronization,
  requestAccess,
  searchClients,
  updatePhysicalIntegration,
  type AccessDecision,
  type AccessDevice,
  type AccessLog,
  type ClientSummary,
  type PhysicalClientState,
  type PhysicalIntegration,
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
  const [manualReason, setManualReason] = useState("");
  const [temporaryReason, setTemporaryReason] = useState("");
  const [temporaryHours, setTemporaryHours] = useState(4);
  const [temporaryMaxUses, setTemporaryMaxUses] = useState(1);
  const [physicalIntegration, setPhysicalIntegration] = useState<PhysicalIntegration | null>(null);
  const [physicalClientState, setPhysicalClientState] = useState<PhysicalClientState | null>(null);
  const [physicalConnectionName, setPhysicalConnectionName] = useState("");
  const [physicalConnectorKey, setPhysicalConnectorKey] = useState("");
  const [physicalSyncEnabled, setPhysicalSyncEnabled] = useState(false);
  const [physicalMappingDrafts, setPhysicalMappingDrafts] = useState<Record<string, { reference: string; name: string }>>({});
  const [externalPersonRef, setExternalPersonRef] = useState("");
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
        const [loadedDevices, loadedLogs, loadedClients, loadedPhysicalIntegration] = await Promise.all([
          getAccessDevices(stored.accessToken),
          getAccessLogs(stored.accessToken),
          searchClients(stored.accessToken, "", "ACTIVE"),
          getPhysicalIntegration(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setDevices(loadedDevices);
        setLogs(loadedLogs);
        setClients(loadedClients);
        setDeviceCode(loadedDevices.find((device) => device.status === "ACTIVE")?.deviceCode ?? "");
        hydratePhysicalIntegration(loadedPhysicalIntegration);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar accesos.");
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

  useEffect(() => {
    if (!session || !selectedClientId) {
      return;
    }
    let active = true;
    const accessToken = session.accessToken;
    async function loadPhysicalClient() {
      try {
        const state = await getPhysicalClientState(accessToken, selectedClientId);
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
      setError(exception instanceof Error ? exception.message : "No fue posible buscar clientes.");
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
      });
      setLastDecision(decision);
      setMessage(decision.decision === "GRANTED" ? "Acceso concedido." : "Acceso denegado.");
      await refreshLogs(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible evaluar acceso.");
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
      setError(exception instanceof Error ? exception.message : "No fue posible abrir manualmente.");
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
      const expiresAt = new Date(Date.now() + temporaryHours * 60 * 60 * 1000).toISOString();
      const pass = await createTemporaryPass(session.accessToken, {
        clientId: selectedClientId,
        expiresAt,
        maxUses: temporaryMaxUses,
        reason: temporaryReason,
      });
      setTemporaryReason("");
      setMessage(`Autorización temporal creada para ${pass.clientName}.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la autorización temporal.");
    } finally {
      setBusy(false);
    }
  }

  function hydratePhysicalIntegration(integration: PhysicalIntegration) {
    const mappedByBenefit = new Map(integration.mappings.map((mapping) => [mapping.benefitId, mapping]));
    setPhysicalIntegration(integration);
    setPhysicalConnectionName(integration.connection.displayName);
    setPhysicalConnectorKey(integration.connection.connectorKey ?? "");
    setPhysicalSyncEnabled(integration.connection.enabled);
    setPhysicalMappingDrafts(Object.fromEntries(integration.availableBenefits.map((benefit) => {
      const mapping = mappedByBenefit.get(benefit.id);
      return [benefit.id, {
        reference: mapping?.externalAccessLevelRef ?? "",
        name: mapping?.externalAccessLevelName ?? "",
      }];
    })));
  }

  function setPhysicalMapping(benefitId: string, field: "reference" | "name", value: string) {
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
        providerKey: "HIKCENTRAL",
        displayName: physicalConnectionName,
        connectorKey: physicalConnectorKey || null,
        benefitMappings: physicalIntegration.availableBenefits.flatMap((benefit) => {
          const draft = physicalMappingDrafts[benefit.id];
          if (!draft?.reference.trim()) {
            return [];
          }
          return [{
            benefitId: benefit.id,
            externalAccessLevelRef: draft.reference.trim(),
            externalAccessLevelName: draft.name.trim() || null,
          }];
        }),
      });
      hydratePhysicalIntegration(integration);
      setMessage("Configuración de acceso físico guardada. No se almacenaron secretos del proveedor.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar la configuración física.");
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
      const state = await linkPhysicalClient(session.accessToken, selectedClientId, externalPersonRef.trim());
      setPhysicalClientState(state);
      setExternalPersonRef(state.externalPersonRef ?? "");
      setMessage("Vínculo con la persona del proveedor guardado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible vincular al socio con el proveedor físico.");
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
      const state = await queuePhysicalClientSynchronization(session.accessToken, selectedClientId);
      setPhysicalClientState(state);
      setMessage(state.syncState?.status === "BLOCKED"
        ? "La sincronización quedó bloqueada hasta completar el mapeo de beneficios."
        : "Sincronización preparada para el conector local.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible preparar la sincronización.");
    } finally {
      setBusy(false);
    }
  }

  return {
    busy,
    clients,
    deviceCode,
    devices,
    error,
    identifier,
    lastDecision,
    loading,
    logs,
    manualReason,
    message,
    physicalClientState,
    physicalConnectionName,
    physicalConnectorKey,
    physicalIntegration,
    physicalMappingDrafts,
    physicalSyncEnabled,
    query,
    selectedClient,
    selectedClientId,
    session,
    externalPersonRef,
    temporaryHours,
    temporaryMaxUses,
    temporaryReason,
    createPass,
    evaluateAccess,
    linkSelectedPhysicalClient,
    openManually,
    queueSelectedPhysicalSynchronization,
    refreshLogs,
    savePhysicalIntegration,
    search,
    selectClient,
    setDeviceCode,
    setIdentifier,
    setManualReason,
    setExternalPersonRef,
    setPhysicalConnectionName,
    setPhysicalConnectorKey,
    setPhysicalMapping,
    setPhysicalSyncEnabled,
    setQuery,
    setTemporaryHours,
    setTemporaryMaxUses,
    setTemporaryReason,
  };
}
