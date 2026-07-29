"use client";

import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  AccessClientSection,
  AccessDecisionSection,
  AccessDeviceSection,
  AccessLogsSection,
  ManualAccessSection,
  TemporaryPassSection,
  VisitPassesSection,
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
      subtitle="Pases, visitas, apertura manual y bitacora de accesos."
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
          {access.message ? <Notice text={access.message} /> : null}

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

          <VisitPassesSection
            busy={access.busy}
            onConsumeVisit={access.consumeVisit}
            onCreateVisitPack={access.createVisitPack}
            onRefresh={access.refreshVisitPasses}
            onValidDaysChange={access.setVisitPassValidDays}
            onVisitPassTotalChange={access.setVisitPassTotal}
            onVisitPassTypeChange={access.setVisitPassType}
            selectedClientId={access.selectedClientId}
            validDays={access.visitPassValidDays}
            visitPasses={access.visitPasses}
            visitPassTotal={access.visitPassTotal}
            visitPassType={access.visitPassType}
          />

          <AccessLogsSection logs={access.logs} onRefreshLogs={() => void access.refreshLogs()} />
        </section>
      </section>
    </AppShell>
  );
}

function Notice({ text }: { text: string }) {
  return (
    <p className="rounded-md border border-[#bfd6c7] bg-[#f0f7f2] px-3 py-2 text-sm text-[#31543d]">
      {text}
    </p>
  );
}

