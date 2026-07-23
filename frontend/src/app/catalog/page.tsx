"use client";

import { Suspense } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { CatalogToolbar, PlanDrawer, PlanList } from "./CatalogSections";
import { useCatalogController } from "./useCatalogController";
import { CatalogFilters } from "@/features/catalog/components/CatalogFilters";
import { CatalogProductDrawer } from "@/features/catalog/components/CatalogProductDrawer";
import { CatalogProductTable } from "@/features/catalog/components/CatalogProductTable";
import { CatalogSummaryCards } from "@/features/catalog/components/CatalogSummaryCards";
import { CatalogCategoryManager } from "@/features/catalog/components/CatalogCategoryManager";

export default function CatalogPage() {
  return (
    <Suspense fallback={<LoadingScreen text="Cargando catalogo..." />}>
      <CatalogContent />
    </Suspense>
  );
}

function CatalogContent() {
  const controller = useCatalogController();
  if (controller.loading && !controller.session) return <LoadingScreen text="Cargando catalogo..." />;
  if (!controller.session) return <AuthRequired />;

  return (
    <AppShell eyebrow="Administracion" session={controller.session}
      subtitle="Productos, servicios y planes disponibles para vender desde recepcion." title="Catalogo">
      {controller.error ? <InlineError message={controller.error} /> : null}
      {controller.message ? <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">{controller.message}</p> : null}
      <section className="grid gap-6">
        <CatalogToolbar canCreate={controller.canCreate} onNewPlan={controller.openPlanCreation} onNewProduct={controller.openProductCreation} />
        <CatalogSummaryCards summary={controller.summary} />
        <CatalogFilters categories={controller.categories} filters={controller.catalogFilters.filters}
          searchInput={controller.catalogFilters.searchInput} total={controller.totalProducts}
          onClear={controller.catalogFilters.clear} onSearchChange={controller.catalogFilters.setSearchInput}
          onUpdate={controller.catalogFilters.update} />
        <CatalogProductTable canArchive={controller.canArchive} canManageStatus={controller.canManageStatus}
          canUpdate={controller.canUpdate} loading={controller.loading} products={controller.products}
          saving={controller.saving} onArchive={controller.archiveCatalogProduct} onEdit={controller.openProductEdition}
          onRestore={controller.restoreCatalogProduct} onToggleStatus={controller.toggleProductStatus} />
        <CatalogPagination page={controller.catalogFilters.filters.page ?? 0} totalPages={controller.totalPages}
          onPageChange={(page) => controller.catalogFilters.update("page", String(page))} />
        {controller.canManageCategories ? <CatalogCategoryManager accessToken={controller.session.accessToken} categories={controller.categories} onChanged={controller.reloadCatalog} /> : null}
        <section className="grid gap-6">
          <PlanList canUpdate={controller.canUpdate} plans={controller.plans} onEditPlan={controller.openPlanEdition} />
        </section>
      </section>
      <CatalogProductDrawer categories={controller.categories} canReadCosts={controller.canReadCosts}
        editing={Boolean(controller.editingProductId)} form={controller.form} open={controller.showProductDrawer}
        price={controller.price} image={controller.productImage} saving={controller.saving}
        onClose={controller.resetProductDrawer} onFormChange={controller.setForm}
        onPriceChange={controller.setPrice} onImageChange={controller.setProductImage} onSubmit={controller.submitProduct} />
      <PlanDrawer editingPlanId={controller.editingPlanId} form={controller.planForm} open={controller.showPlanDrawer}
        saving={controller.saving} onClose={controller.resetPlanDrawer} onFormChange={controller.setPlanForm}
        onSubmit={controller.submitPlan} />
    </AppShell>
  );
}

function CatalogPagination({ onPageChange, page, totalPages }: { onPageChange: (page: number) => void; page: number; totalPages: number; }) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Paginacion del catalogo" className="flex items-center justify-end gap-3">
      <button className="app-button-secondary" disabled={page <= 0} onClick={() => onPageChange(page - 1)} type="button">Anterior</button>
      <span className="text-sm text-[#5d6b63]">Pagina {page + 1} de {totalPages}</span>
      <button className="app-button-secondary" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)} type="button">Siguiente</button>
    </nav>
  );
}

function AuthRequired() {
  return <AuthRequiredCard message="El catalogo requiere una cuenta con permisos para consultar o administrar productos y planes." title="Inicia sesion para ver el catalogo" />;
}
