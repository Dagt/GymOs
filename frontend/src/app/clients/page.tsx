"use client";

import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { FormError } from "@/components/FormFields";
import {
  ClientList,
  ClientSearchBar,
  ClientToolbar,
  CreateClientDrawer,
} from "./ClientSections";
import { useClientsController } from "./useClientsController";

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
      session={controller.session}
      subtitle="Registro completo de socios, membresia inicial y busqueda para recepcion."
      title="Socios"
    >
      <ClientSearchBar query={controller.query} onQueryChange={controller.setQuery} onSubmit={controller.submitSearch} />

      {controller.error ? <FormError message={controller.error} /> : null}
      {controller.message ? <p className="form-message success">{controller.message}</p> : null}

      <section className="grid gap-6">
        <ClientToolbar onCreateClient={() => controller.setShowCreateClient(true)} />
        <ClientList clients={controller.clients} loading={controller.loading} />
      </section>

      <CreateClientDrawer
        createdClientId={controller.createdClientId}
        errors={controller.fieldErrors}
        form={controller.form}
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
      message="La lista de socios contiene informacion sensible y requiere una cuenta autorizada."
      title="Inicia sesion para consultar socios"
    />
  );
}
