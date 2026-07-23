import { type FormEvent, useEffect, useMemo, useState } from "react";
import {
  consumeVisitPass,
  createTemporaryPass,
  createVisitPass,
  getAccessDevices,
  getAccessLogs,
  getStoredSession,
  getVisitPasses,
  manualOpenAccess,
  requestAccess,
  searchClients,
  type AccessDecision,
  type AccessDevice,
  type AccessLog,
  type ClientSummary,
  type StoredSession,
  type VisitPass,
} from "@/lib/api";
import { type VisitPassType } from "./AccessSections";

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
  const [visitPasses, setVisitPasses] = useState<VisitPass[]>([]);
  const [visitPassType, setVisitPassType] = useState<VisitPassType>("VISIT_PACK");
  const [visitPassTotal, setVisitPassTotal] = useState(5);
  const [visitPassValidDays, setVisitPassValidDays] = useState(30);
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
        const [loadedDevices, loadedLogs, loadedClients] = await Promise.all([
          getAccessDevices(stored.accessToken),
          getAccessLogs(stored.accessToken),
          searchClients(stored.accessToken, "", "ACTIVE"),
        ]);
        if (!active) {
          return;
        }
        setDevices(loadedDevices);
        setLogs(loadedLogs);
        setClients(loadedClients);
        setDeviceCode(loadedDevices.find((device) => device.status === "ACTIVE")?.deviceCode ?? "");
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
    const accessToken = session.accessToken;
    let active = true;
    async function loadVisitPasses() {
      try {
        const passes = await getVisitPasses(accessToken, selectedClientId);
        if (active) {
          setVisitPasses(passes);
        }
      } catch {
        if (active) {
          setVisitPasses([]);
        }
      }
    }
    void loadVisitPasses();
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
    setVisitPasses([]);
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
      setMessage(`Pase temporal creado para ${pass.clientName}.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear pase temporal.");
    } finally {
      setBusy(false);
    }
  }

  async function createVisitPack() {
    if (!session || !selectedClientId) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const validFrom = new Date();
      const validUntil = new Date(Date.now() + visitPassValidDays * 24 * 60 * 60 * 1000);
      const pass = await createVisitPass(session.accessToken, {
        clientId: selectedClientId,
        passType: visitPassType,
        totalVisits: visitPassTotal,
        validFrom: toDateOnly(validFrom),
        validUntil: toDateOnly(validUntil),
      });
      setVisitPasses((current) => [pass, ...current.filter((item) => item.id !== pass.id)]);
      setMessage(`Paquete creado para ${pass.clientName}.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear paquete de visitas.");
    } finally {
      setBusy(false);
    }
  }

  async function consumeVisit(visitPassId: string) {
    if (!session) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const pass = await consumeVisitPass(session.accessToken, visitPassId, {
        reason: "Consumo registrado en recepcion",
      });
      setVisitPasses((current) => current.map((item) => (item.id === pass.id ? pass : item)));
      setMessage(`Visita consumida. Quedan ${pass.remainingVisits}.`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible consumir la visita.");
    } finally {
      setBusy(false);
    }
  }

  async function refreshVisitPasses() {
    if (!session || !selectedClientId) {
      return;
    }
    setVisitPasses(await getVisitPasses(session.accessToken, selectedClientId));
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
    query,
    selectedClient,
    selectedClientId,
    session,
    temporaryHours,
    temporaryMaxUses,
    temporaryReason,
    visitPasses,
    visitPassTotal,
    visitPassType,
    visitPassValidDays,
    consumeVisit,
    createPass,
    createVisitPack,
    evaluateAccess,
    openManually,
    refreshLogs,
    refreshVisitPasses,
    search,
    selectClient,
    setDeviceCode,
    setIdentifier,
    setManualReason,
    setQuery,
    setTemporaryHours,
    setTemporaryMaxUses,
    setTemporaryReason,
    setVisitPassTotal,
    setVisitPassType,
    setVisitPassValidDays,
  };
}

function toDateOnly(value: Date) {
  return value.toISOString().slice(0, 10);
}
