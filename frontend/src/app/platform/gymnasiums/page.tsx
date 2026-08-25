"use client";

import { AppDrawer } from "@/components/AppDrawer";
import { AppNotice } from "@/components/AppNotice";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import { FormError } from "@/components/FormFields";
import { BranchForm, CustomerAccountForm, OrganizationForm } from "./PlatformGymnasiumForms";
import { PlatformGymnasiumDetail, PlatformGymnasiumList } from "./PlatformGymnasiumPanels";
import { usePlatformGymnasiumsController } from "./usePlatformGymnasiumsController";
import { invitationDeliveryDescription, invitationDeliveryTitle } from "./owner-invitation";

export default function PlatformGymnasiumsPage() {
  const platform = usePlatformGymnasiumsController();

  if (platform.loading) {
    return <LoadingScreen text="Cargando administracion de gimnasios..." />;
  }

  if (!platform.session) {
    return (
      <AuthRequired
        message="El panel de plataforma permite crear gimnasios, cambiar de gimnasio y seleccionar sucursal operativa."
        title="Inicia sesion como administrador de plataforma"
      />
    );
  }

  return (
    <AppShell
      branchName={platform.activeBranch?.name}
      eyebrow="Plataforma GymFort"
      mode="setup"
      organizationName="GymFort"
      session={platform.session}
      subtitle="Tus clientes, sus sucursales, marca, usuarios y acceso de soporte en un solo lugar."
      title="CRM de gimnasios cliente"
      variant="platform"
    >
      {platform.error ? <FormError message={platform.error} /> : null}
      {platform.message ? <AppNotice message={platform.message} tone="success" /> : null}
	  {platform.provisionedInvitation ? (
		<section className="app-card platform-invitation-card" role="status">
		  <div>
			<p className="app-eyebrow">Invitación del dueño</p>
			<h2>{invitationDeliveryTitle(platform.provisionedInvitation.emailDeliveryStatus)}</h2>
			<span>
			  {invitationDeliveryDescription(
				platform.provisionedInvitation.emailDeliveryStatus,
				platform.provisionedInvitation.ownerEmail,
			  )} Vence el {formatDateTime(platform.provisionedInvitation.invitationExpiresAt)}.
			</span>
		  </div>
		  <div className="platform-invitation-actions">
			<input aria-label="Enlace de respaldo de la invitación" readOnly value={platform.provisionedInvitation.invitationUrl} />
			<button className="app-button" onClick={() => navigator.clipboard.writeText(platform.provisionedInvitation!.invitationUrl)} type="button">Copiar enlace de respaldo</button>
			<button className="app-button-secondary" onClick={() => platform.setProvisionedInvitation(null)} type="button">Ocultar</button>
		  </div>
		</section>
	  ) : null}

      <section className="platform-kpi-grid" aria-label="Resumen de clientes">
        <article className="platform-kpi"><span>Clientes</span><strong>{platform.portfolio?.summary.customerCount ?? 0}</strong></article>
        <article className="platform-kpi"><span>Activos</span><strong>{platform.portfolio?.summary.activeCustomerCount ?? 0}</strong></article>
        <article className="platform-kpi"><span>En implementación</span><strong>{platform.portfolio?.summary.onboardingCustomerCount ?? 0}</strong></article>
        <article className={`platform-kpi ${(platform.portfolio?.summary.atRiskCustomerCount ?? 0) > 0 ? "danger" : "neutral"}`}><span>En riesgo</span><strong>{platform.portfolio?.summary.atRiskCustomerCount ?? 0}</strong></article>
        <article className="platform-kpi"><span>MRR configurado</span><strong>{formatCurrency(platform.portfolio?.summary.monthlyRecurringRevenue ?? 0)}</strong></article>
      </section>

      <section className="platform-layout">
        <PlatformGymnasiumList
          customers={platform.portfolio?.customers ?? []}
          filteredOrganizations={platform.filteredOrganizations}
          lifecycleFilter={platform.lifecycleFilter}
          onCreateOrganization={platform.openCreateOrganization}
          onLifecycleFilterChange={platform.setLifecycleFilter}
          onQueryChange={platform.setQuery}
          onSelectOrganization={platform.setSelectedOrganizationId}
          organizationsCount={platform.organizations.length}
          query={platform.query}
          selectedOrganizationId={platform.selectedOrganization?.id ?? ""}
        />

        <PlatformGymnasiumDetail
          activeBranchId={platform.session.user.branchId}
          branches={platform.branches}
          branchesLoading={platform.branchesLoading}
          onActivateBranch={platform.activateBranch}
          onActivateOrganization={platform.activateOrganization}
          onCreateBranch={platform.openBranchDrawer}
          onEditCustomerAccount={platform.openCustomerAccount}
          onGoToTenantSection={platform.goToTenantSection}
          onReissueOwnerInvitation={platform.reissueOwnerInvitation}
          onToggleOrganizationStatus={platform.toggleOrganizationStatus}
          saving={platform.saving}
          selectedIsActive={platform.selectedIsActive}
          selectedCustomer={platform.selectedCustomer}
          selectedOrganization={platform.selectedOrganization}
          selectingId={platform.selectingId}
        />
      </section>

      <AppDrawer
		description="El acceso al tenant será temporal, quedará asociado a tu usuario y se registrará en auditoría."
		onClose={platform.closeSupportReason}
		open={platform.showSupportReason}
		title="Motivo de soporte"
	  >
		<form className="grid gap-4" onSubmit={platform.submitSupportReason}>
		  <label className="grid gap-2 font-semibold">
			Motivo del acceso
			<textarea
			  autoFocus
			  maxLength={500}
			  onChange={(event) => platform.setSupportReason(event.target.value)}
			  rows={4}
			  value={platform.supportReason}
			/>
		  </label>
		  {platform.supportReasonError ? <FormError message={platform.supportReasonError} /> : null}
		  <div className="app-form-actions">
			<button className="app-button-secondary" onClick={platform.closeSupportReason} type="button">Cancelar</button>
			<button className="app-button" type="submit">Iniciar soporte temporal</button>
		  </div>
		</form>
	  </AppDrawer>

      <AppDrawer
		description="Crea de una vez el tenant, su primera sucursal y la cuenta invitada del dueño. Si algo falla, no queda un gimnasio huérfano."
        onClose={platform.closeCreateOrganization}
        open={platform.showCreate}
        title="Alta de gimnasio"
      >
        <OrganizationForm
          errors={platform.fieldErrors}
          form={platform.form}
          onLogoFileChange={platform.setLogoFile}
          onChange={platform.setForm}
          onSubmit={platform.submitOrganization}
          saving={platform.saving}
        />
      </AppDrawer>

      <AppDrawer
        description={
          platform.selectedOrganization
            ? `Nueva sede para ${platform.selectedOrganization.tradeName}.`
            : "Nueva sede del gimnasio seleccionado."
        }
        onClose={() => platform.setShowCreateBranch(false)}
        open={platform.showCreateBranch}
        title="Crear sucursal"
      >
        <BranchForm
          errors={platform.branchFieldErrors}
          form={platform.branchForm}
          onChange={platform.setBranchForm}
          onSubmit={platform.submitBranch}
          saving={platform.saving}
        />
      </AppDrawer>

      <AppDrawer
        description={platform.selectedOrganization ? `Relacion comercial interna con ${platform.selectedOrganization.tradeName}.` : undefined}
        onClose={platform.closeCustomerAccount}
        open={platform.showCustomerAccount}
        title="Expediente comercial"
        wide
      >
        {platform.error ? <FormError message={platform.error} /> : null}
        {platform.customerForm ? (
          <CustomerAccountForm
            form={platform.customerForm}
            onChange={platform.setCustomerForm}
            onSubmit={platform.submitCustomerAccount}
            saving={platform.saving}
          />
        ) : null}
      </AppDrawer>
    </AppShell>
  );
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency: "MXN",
    maximumFractionDigits: 0,
  }).format(value);
}

function formatDateTime(value: string) {
	return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
