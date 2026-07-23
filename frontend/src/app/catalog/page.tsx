"use client";

import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  CatalogToolbar,
  PlanDrawer,
  PlanList,
  ProductDrawer,
  ProductList,
} from "./CatalogSections";
import { useCatalogController } from "./useCatalogController";

export default function CatalogPage() {
  const controller = useCatalogController();

  if (controller.loading && !controller.session) {
    return <LoadingScreen text="Cargando catalogo..." />;
  }

  if (!controller.session) {
    return <AuthRequired />;
  }

  return (
    <AppShell
      eyebrow="Administracion"
      session={controller.session}
      subtitle="Productos, servicios y planes disponibles para vender desde recepcion."
      title="Catalogo"
    >
      {controller.error ? <InlineError message={controller.error} /> : null}
      {controller.message ? (
        <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">
          {controller.message}
        </p>
      ) : null}

      <section className="grid gap-6">
        <CatalogToolbar
          onNewPlan={controller.openPlanCreation}
          onNewProduct={() => controller.setShowProductDrawer(true)}
        />

        <section className="grid gap-6 lg:grid-cols-2">
          <ProductList loading={controller.loading} products={controller.sellableProducts} />
          <PlanList plans={controller.plans} onEditPlan={controller.openPlanEdition} />
        </section>
      </section>

      <ProductDrawer
        form={controller.form}
        open={controller.showProductDrawer}
        price={controller.price}
        saving={controller.saving}
        onClose={() => controller.setShowProductDrawer(false)}
        onFormChange={controller.setForm}
        onPriceChange={controller.setPrice}
        onSubmit={controller.submitProduct}
      />

      <PlanDrawer
        editingPlanId={controller.editingPlanId}
        form={controller.planForm}
        open={controller.showPlanDrawer}
        saving={controller.saving}
        onClose={controller.resetPlanDrawer}
        onFormChange={controller.setPlanForm}
        onSubmit={controller.submitPlan}
      />
    </AppShell>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="El catalogo requiere una cuenta con permisos para consultar o administrar productos y planes."
      title="Inicia sesion para ver el catalogo"
    />
  );
}
