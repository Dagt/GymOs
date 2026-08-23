"use client";

import { AppDrawer } from "@/components/AppDrawer";
import { AppNotice } from "@/components/AppNotice";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import { FormError } from "@/components/FormFields";
import Link from "next/link";
import { BranchForm, OrganizationForm } from "./PlatformGymnasiumForms";
import { PlatformGymnasiumDetail, PlatformGymnasiumList } from "./PlatformGymnasiumPanels";
import { usePlatformGymnasiumsController } from "./usePlatformGymnasiumsController";

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

      <section className="platform-billing-entry">
        <div>
          <p className="app-eyebrow">Administración GymFort</p>
          <h2>Cobros y suscripciones</h2>
          <span>Consulta el plan contratado, socios activos, MRR, fechas de corte e historial de pagos de cada gimnasio.</span>
        </div>
        <Link className="app-button" href="/platform/billing">Abrir cobros y suscripciones</Link>
      </section>

      <section className="platform-layout">
        <PlatformGymnasiumList
          filteredOrganizations={platform.filteredOrganizations}
          onCreateOrganization={platform.openCreateOrganization}
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
          onGoToTenantSection={platform.goToTenantSection}
          onToggleOrganizationStatus={platform.toggleOrganizationStatus}
          saving={platform.saving}
          selectedIsActive={platform.selectedIsActive}
          selectedOrganization={platform.selectedOrganization}
          selectingId={platform.selectingId}
        />
      </section>

      <AppDrawer
        description="Registra el tenant y su marca. Las sucursales se crean despues desde el detalle del gimnasio."
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
    </AppShell>
  );
}
