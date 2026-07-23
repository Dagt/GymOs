"use client";

import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { FormError } from "@/components/FormFields";
import {
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

  if (!controller.session) {
    return <AuthRequired />;
  }

  if (controller.loading || !controller.form || !controller.data) {
    return <LoadingScreen text="Cargando ficha de socio..." />;
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

      <section className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <section className="grid gap-6">
          <ClientProfileCard client={controller.data.client} onEdit={() => controller.setShowEditDrawer(true)} />
          <MembershipHistory
            changingMembershipId={controller.changingMembershipId}
            memberships={controller.memberships}
            onMembershipAction={controller.openMembershipAction}
            plans={controller.plans}
            renewingMembershipId={controller.renewingMembershipId}
          />
        </section>

        <ClientSummaryAside data={controller.data} />
      </section>

      <EditClientDrawer
        errors={controller.fieldErrors}
        form={controller.form}
        open={controller.showEditDrawer}
        saving={controller.saving}
        onClose={() => controller.setShowEditDrawer(false)}
        onFieldChange={controller.updateField}
        onSubmit={controller.submit}
      />

      <MembershipActionDrawer
        action={controller.membershipAction}
        changingMembershipId={controller.changingMembershipId}
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
