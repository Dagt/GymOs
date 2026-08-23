"use client";

import { useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AppNotice } from "@/components/AppNotice";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  AccessOperationSection,
  AccessLogsSection,
  PhysicalAccessIntegrationSection,
} from "./AccessSections";
import { useAccessController } from "./useAccessController";

type AccessTab = "OPERATE" | "PHYSICAL" | "LOGS";

export default function AccessPage() {
  const access = useAccessController();
  const [activeTab, setActiveTab] = useState<AccessTab>("OPERATE");

  if (access.loading) {
    return <LoadingScreen text="Cargando accesos..." />;
  }

  if (!access.session) {
    return (
      <AuthRequiredCard
        message="Necesitas una cuenta con permisos de accesos para abrir esta seccion."
        title="Inicia sesion para ver accesos"
      />
    );
  }

  return (
    <AppShell
      eyebrow="Operacion"
      session={access.session}
      subtitle="Valida entradas, configura puntos físicos y consulta la bitácora de tu sucursal."
      title="Control de acceso"
    >
      <section className="grid gap-6">
        {access.error ? <InlineError message={access.error} /> : null}
        {access.message ? (
          <AppNotice message={access.message} tone="success" />
        ) : null}

        <nav
          aria-label="Secciones de control de acceso"
          className="grid gap-2 rounded-2xl border border-[var(--brand-line)] bg-white p-2 shadow-sm sm:grid-cols-3"
        >
          <TabButton
            active={activeTab === "OPERATE"}
            description="Validar entradas y excepciones"
            label="Operar acceso"
            onClick={() => setActiveTab("OPERATE")}
          />
          <TabButton
            active={activeTab === "PHYSICAL"}
            description="Puntos, derechos y HikCentral"
            label="Configuración física"
            onClick={() => setActiveTab("PHYSICAL")}
          />
          <TabButton
            active={activeTab === "LOGS"}
            description="Resultados y evidencia reciente"
            label="Bitácora"
            onClick={() => setActiveTab("LOGS")}
          />
        </nav>

        {activeTab === "OPERATE" ? (
          <AccessOperationSection
            availableBenefits={
              access.physicalIntegration?.availableBenefits ?? []
            }
            busy={access.busy}
            clients={access.clients}
            deviceCode={access.deviceCode}
            deviceRequiredBenefitIds={access.deviceRequiredBenefitIds}
            devices={access.devices}
            identifier={access.identifier}
            lastDecision={access.lastDecision}
            manualReason={access.manualReason}
            maxUses={access.temporaryMaxUses}
            onCreatePass={access.createPass}
            onDeviceCodeChange={access.setDeviceCode}
            onEvaluate={access.evaluateAccess}
            onIdentifierChange={access.setIdentifier}
            onManualReasonChange={access.setManualReason}
            onMaxUsesChange={access.setTemporaryMaxUses}
            onOpenManually={access.openManually}
            onQueryChange={access.setQuery}
            onSearch={access.search}
            onSelectClient={access.selectClient}
            onTemporaryHoursChange={access.setTemporaryHours}
            onTemporaryReasonChange={access.setTemporaryReason}
            query={access.query}
            selectedClient={access.selectedClient}
            selectedClientId={access.selectedClientId}
            temporaryHours={access.temporaryHours}
            temporaryReason={access.temporaryReason}
          />
        ) : null}

        {activeTab === "PHYSICAL" ? (
          <PhysicalAccessIntegrationSection
            availableDeviceBenefits={
              access.physicalIntegration?.availableBenefits ?? []
            }
            busy={access.busy}
            connectionName={access.physicalConnectionName}
            connectorKey={access.physicalConnectorKey}
            device={access.selectedDevice}
            deviceCode={access.deviceCode}
            deviceRequiredBenefitIds={access.deviceRequiredBenefitIds}
            devices={access.devices}
            externalPersonRef={access.externalPersonRef}
            integration={access.physicalIntegration}
            mappingDrafts={access.physicalMappingDrafts}
            onConnectionNameChange={access.setPhysicalConnectionName}
            onConnectorKeyChange={access.setPhysicalConnectorKey}
            onCreateDevice={access.createDevice}
            onDeviceCodeChange={access.setDeviceCode}
            onExternalPersonRefChange={access.setExternalPersonRef}
            onLinkClient={access.linkSelectedPhysicalClient}
            onMappingChange={access.setPhysicalMapping}
            onQueueSynchronization={access.queueSelectedPhysicalSynchronization}
            onSaveDeviceRequiredBenefits={access.saveDeviceRequiredBenefits}
            onSaveIntegration={access.savePhysicalIntegration}
            onSyncEnabledChange={access.setPhysicalSyncEnabled}
            physicalClientState={access.physicalClientState}
            selectedClient={access.selectedClient}
            syncEnabled={access.physicalSyncEnabled}
          />
        ) : null}

        {activeTab === "LOGS" ? (
          <AccessLogsSection
            logs={access.logs}
            onRefreshLogs={() => void access.refreshLogs()}
          />
        ) : null}
      </section>
    </AppShell>
  );
}

function TabButton({
  active,
  description,
  label,
  onClick,
}: {
  active: boolean;
  description: string;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      className={`rounded-xl px-4 py-3 text-left transition ${
        active
          ? "bg-[var(--brand-primary)] text-white shadow-sm"
          : "text-[var(--brand-ink)] hover:bg-[var(--brand-surface)]"
      }`}
      onClick={onClick}
      type="button"
    >
      <span className="block text-sm font-black">{label}</span>
      <span
        className={`mt-1 block text-xs ${active ? "text-white/70" : "text-[var(--brand-muted)]"}`}
      >
        {description}
      </span>
    </button>
  );
}
