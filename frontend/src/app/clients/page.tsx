"use client";

import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AppNotice } from "@/components/AppNotice";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { FormError } from "@/components/FormFields";
import {
  ClientList,
  ClientSearchBar,
  ClientToolbar,
  CreateClientDrawer,
} from "./ClientSections";
import { useClientsController } from "./useClientsController";
import "./clients-scroll.css";

export default function ClientsPage() {
  const controller = useClientsController();

  if (controller.loading && !controller.session) {
    return <LoadingScreen text="Cargando socios..." />;
  }

  if (!controller.session) {
    return <AuthRequired />;
  }

  return (
    <AppShell
      eyebrow="Socios"
      pageClassName="clients-scroll-page"
      session={controller.session}
      subtitle="Registro de socios, membresía inicial opcional y búsqueda para recepción."
      title="Socios"
    >
      <ClientSearchBar query={controller.query} onQueryChange={controller.setQuery} onSubmit={controller.submitSearch} />

      {controller.error ? <FormError message={controller.error} /> : null}
      {controller.message ? <AppNotice message={controller.message} tone="success" /> : null}

      <section className="grid gap-6">
        <ClientToolbar
          canCreate={controller.canCreateClient}
          onCreateClient={() => controller.setShowCreateClient(true)}
        />
        <ClientList clients={controller.clients} loading={controller.loading} />
      </section>

      <CreateClientDrawer
        canRegisterMembership={controller.canCreateMembershipSale}
        createdClientId={controller.createdClientId}
        errors={controller.fieldErrors}
        form={controller.form}
        manualCardEnabled={controller.manualCardEnabled}
        mercadoPagoEnabled={controller.mercadoPagoEnabled}
        open={controller.showCreateClient}
        plans={controller.plans}
        saving={controller.saving}
        onClose={() => controller.setShowCreateClient(false)}
        onFormChange={controller.updateForm}
        onSubmit={controller.submitCreate}
      />
    </AppShell>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="La lista de socios contiene información sensible y requiere una cuenta autorizada."
      title="Inicia sesión para consultar socios"
    />
  );
}
