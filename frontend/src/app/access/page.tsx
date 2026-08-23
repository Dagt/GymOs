"use client";

import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AppNotice } from "@/components/AppNotice";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  AccessClientSection,
  AccessDecisionSection,
  AccessDeviceSection,
  AccessLogsSection,
  ManualAccessSection,
  PhysicalAccessIntegrationSection,
  TemporaryPassSection,
} from "./AccessSections";
import { useAccessController } from "./useAccessController";

export default function AccessPage() {
  const access = useAccessController();

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
      subtitle="Consola operativa, autorizaciones excepcionales y sincronización de derechos físicos."
      title="Control de acceso"
    >
      <section className="grid gap-6 lg:grid-cols-[0.95fr_1.4fr]">
        <aside className="grid gap-6">
          <AccessDeviceSection
            deviceCode={access.deviceCode}
            devices={access.devices}
            onDeviceCodeChange={access.setDeviceCode}
          />
          <AccessClientSection
            busy={access.busy}
            clients={access.clients}
            identifier={access.identifier}
            onIdentifierChange={access.setIdentifier}
            onQueryChange={access.setQuery}
            onSearch={access.search}
            onSelectClient={access.selectClient}
            query={access.query}
            selectedClientId={access.selectedClientId}
          />
        </aside>

        <section className="grid gap-6">
          {access.error ? <InlineError message={access.error} /> : null}
          {access.message ? <AppNotice message={access.message} tone="success" /> : null}

          <AccessDecisionSection
            busy={access.busy}
            identifier={access.identifier}
            lastDecision={access.lastDecision}
            onEvaluate={access.evaluateAccess}
            selectedClient={access.selectedClient}
            selectedClientId={access.selectedClientId}
          />

          <section className="grid gap-6 xl:grid-cols-2">
            <ManualAccessSection
              busy={access.busy}
              manualReason={access.manualReason}
              onManualReasonChange={access.setManualReason}
              onOpenManually={access.openManually}
            />
            <TemporaryPassSection
              busy={access.busy}
              maxUses={access.temporaryMaxUses}
              onCreatePass={access.createPass}
              onMaxUsesChange={access.setTemporaryMaxUses}
              onReasonChange={access.setTemporaryReason}
              onTemporaryHoursChange={access.setTemporaryHours}
              reason={access.temporaryReason}
              selectedClientId={access.selectedClientId}
              temporaryHours={access.temporaryHours}
            />
          </section>

          <PhysicalAccessIntegrationSection
            busy={access.busy}
            connectionName={access.physicalConnectionName}
            connectorKey={access.physicalConnectorKey}
            externalPersonRef={access.externalPersonRef}
            integration={access.physicalIntegration}
            mappingDrafts={access.physicalMappingDrafts}
            onConnectionNameChange={access.setPhysicalConnectionName}
            onConnectorKeyChange={access.setPhysicalConnectorKey}
            onExternalPersonRefChange={access.setExternalPersonRef}
            onLinkClient={access.linkSelectedPhysicalClient}
            onMappingChange={access.setPhysicalMapping}
            onQueueSynchronization={access.queueSelectedPhysicalSynchronization}
            onSaveIntegration={access.savePhysicalIntegration}
            onSyncEnabledChange={access.setPhysicalSyncEnabled}
            physicalClientState={access.physicalClientState}
            selectedClient={access.selectedClient}
            syncEnabled={access.physicalSyncEnabled}
          />

          <AccessLogsSection logs={access.logs} onRefreshLogs={() => void access.refreshLogs()} />
        </section>
      </section>
    </AppShell>
  );
}
