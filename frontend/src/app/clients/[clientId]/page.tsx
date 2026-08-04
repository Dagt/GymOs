"use client";

import Link from "next/link";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { FormError } from "@/components/FormFields";
import {
  ClientBenefitsSection,
  ClientProfileCard,
  ClientSummaryAside,
  EditClientDrawer,
  MembershipActionDrawer,
  MembershipHistory,
} from "./ClientDetailSections";
import {
  clientFullName,
  formatDate,
  statusLabel,
} from "./ClientDetailHelpers";
import { useClientDetailController } from "./useClientDetailController";

export default function ClientDetailPage() {
  const controller = useClientDetailController();

  if (!controller.session && controller.loading) {
    return <LoadingScreen text="Cargando ficha de socio..." />;
  }

  if (!controller.session) {
    return <AuthRequired />;
  }

  if (controller.loading) {
    return <LoadingScreen text="Cargando ficha de socio..." />;
  }

  if (!controller.data || !controller.form) {
    return (
      <AppShell
        eyebrow="Socios"
        session={controller.session}
        subtitle="No fue posible recuperar la ficha solicitada."
        title="Ficha no disponible"
      >
        <section className="app-card grid gap-4 p-6">
          <FormError message={controller.error ?? "No fue posible cargar la ficha del socio."} />
          <div>
            <Link className="app-button-secondary inline-flex" href="/clients">
              Volver a socios
            </Link>
          </div>
        </section>
      </AppShell>
    );
  }

  return (
    <AppShell
      eyebrow="Socios"
      session={controller.session}
      subtitle={`Cliente desde ${formatDate(controller.data.client.joinedAt)} - ${statusLabel(controller.data.client.status)}`}
      title={clientFullName(controller.data.client)}
    >
      {controller.error ? <FormError message={controller.error} /> : null}
      {controller.message ? <p className="form-message success">{controller.message}</p> : null}

      {controller.limitedCoachView ? (
        <section className="mb-5 rounded-xl border border-[#d8ddd2] bg-[#f7f9f6] px-4 py-3 text-sm text-[#5d6b63]">
          Vista de entrenamiento: puedes consultar la identidad y los datos de contacto necesarios para asignar rutinas. La información financiera, las membresías y la edición administrativa permanecen restringidas.
        </section>
      ) : null}

      <section className={controller.limitedCoachView ? "grid gap-6" : "grid gap-6 xl:grid-cols-[1.1fr_0.9fr]"}>
        <section className="grid gap-6">
          <ClientProfileCard
            canEdit={controller.canEditClient}
            client={controller.data.client}
            onEdit={() => controller.setShowEditDrawer(true)}
          />
          {controller.canReadMemberships ? (
            <MembershipHistory
              canChange={controller.canChangeMembership}
              canEnroll={controller.canEnrollMembership}
              canRenew={controller.canRenewMembership}
              changingMembershipId={controller.changingMembershipId}
              memberships={controller.memberships}
              onMembershipAction={controller.openMembershipAction}
              plans={controller.plans}
              renewingMembershipId={controller.renewingMembershipId}
            />
          ) : null}
          {controller.canReadBenefits ? (
            <ClientBenefitsSection
              canAssignBenefits={controller.canAssignBenefits}
              canReadAccessBenefits={controller.canReadAccessBenefits}
              canVerifyBenefits={controller.canVerifyBenefits}
              clientId={controller.data.client.id}
              refreshKey={controller.memberships
                .map((membership) => `${membership.id}:${membership.planId}:${membership.status}:${membership.endsOn}`)
                .join("|")}
              session={controller.session}
            />
          ) : null}
        </section>

        {!controller.limitedCoachView ? (
          <ClientSummaryAside
            data={controller.data}
            periodsByMembership={controller.membershipPeriods}
          />
        ) : null}
      </section>

      {controller.canEditClient ? (
        <EditClientDrawer
          errors={controller.fieldErrors}
          form={controller.form}
          open={controller.showEditDrawer}
          saving={controller.saving}
          onClose={() => controller.setShowEditDrawer(false)}
          onFieldChange={controller.updateField}
          onSubmit={controller.submit}
        />
      ) : null}

      {controller.canManageMemberships ? (
        <MembershipActionDrawer
          action={controller.membershipAction}
          changingMembershipId={controller.changingMembershipId}
          membershipQuote={controller.membershipQuote}
          manualCardEnabled={controller.manualCardEnabled}
          mercadoPagoEnabled={controller.mercadoPagoEnabled}
          plans={controller.plans}
          renewMethod={controller.renewMethod}
          renewReference={controller.renewReference}
          renewingMembershipId={controller.renewingMembershipId}
          renewValidateNow={controller.renewValidateNow}
          targetPlanId={controller.targetPlanId}
          onClose={() => controller.setMembershipAction(null)}
          onMethodChange={controller.setRenewMethod}
          onReferenceChange={controller.setRenewReference}
          onSubmit={controller.submitMembershipAction}
          onTargetPlanChange={controller.setTargetPlanId}
          onValidateNowChange={controller.setRenewValidateNow}
        />
      ) : null}
    </AppShell>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="La ficha de socio contiene informacion sensible y requiere una cuenta autorizada."
      title="Inicia sesion para consultar socios"
    />
  );
}
