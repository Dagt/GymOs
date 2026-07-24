"use client";

import { Suspense, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { CatalogToolbar, PlanDrawer, PlanList } from "./CatalogSections";
import { useCatalogController } from "./useCatalogController";
import { CatalogFilters } from "@/features/catalog/components/CatalogFilters";
import { CatalogProductDrawer } from "@/features/catalog/components/CatalogProductDrawer";
import { CatalogProductTable } from "@/features/catalog/components/CatalogProductTable";
import { CatalogSummaryCards } from "@/features/catalog/components/CatalogSummaryCards";
import { CatalogCategoryManager } from "@/features/catalog/components/CatalogCategoryManager";

type CatalogView = "products" | "plans" | "categories";

export default function CatalogPage() {
  return (
    <Suspense fallback={<LoadingScreen text="Cargando catálogo..." />}>
      <CatalogContent />
    </Suspense>
  );
}

function CatalogContent() {
  const controller = useCatalogController();
  const [view, setView] = useState<CatalogView>("products");
  if (controller.loading && !controller.session) return <LoadingScreen text="Cargando catálogo..." />;
  if (!controller.session) return <AuthRequired />;

  function openProductCreation() {
    setView("products");
    controller.openProductCreation();
  }

  function openPlanCreation() {
    setView("plans");
    controller.openPlanCreation();
  }

  return (
    <AppShell eyebrow="Administración" session={controller.session}
      subtitle="Administra artículos de venta, cargos simples de caja, planes y categorías." title="Catálogo">
      {controller.error ? <InlineError message={controller.error} /> : null}
      {controller.message ? <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">{controller.message}</p> : null}
      <section className="grid gap-5">
        <CatalogToolbar canCreate={controller.canCreate} onNewPlan={openPlanCreation} onNewProduct={openProductCreation} />
        <CatalogNavigation canManageCategories={controller.canManageCategories} view={view} onChange={setView} />

        {view === "products" ? (
          <section className="grid gap-5">
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
          </section>
        ) : null}

        {view === "plans" ? (
          <PlanList canUpdate={controller.canUpdate} plans={controller.plans} onEditPlan={controller.openPlanEdition} />
        ) : null}

        {view === "categories" && controller.canManageCategories ? (
          <CatalogCategoryManager accessToken={controller.session.accessToken} categories={controller.categories} onChanged={controller.reloadCatalog} />
        ) : null}
      </section>
      <CatalogProductDrawer categories={controller.categories} canManageImages={controller.canManageImages}
        canReadCosts={controller.canReadCosts} editing={Boolean(controller.editingProductId)}
        existingImage={Boolean(controller.existingImageObjectKey)} form={controller.form}
        open={controller.showProductDrawer} price={controller.price} image={controller.productImage}
        saving={controller.saving} onClose={controller.resetProductDrawer} onFormChange={controller.setForm}
        onPriceChange={controller.setPrice} onImageChange={controller.setProductImage}
        onRemoveImage={controller.removeCurrentProductImage} onSubmit={controller.submitProduct} />
      <PlanDrawer editingPlanId={controller.editingPlanId} form={controller.planForm} open={controller.showPlanDrawer}
        saving={controller.saving} onClose={controller.resetPlanDrawer} onFormChange={controller.setPlanForm}
        onSubmit={controller.submitPlan} />
    </AppShell>
  );
}

function CatalogNavigation({ canManageCategories, onChange, view }: {
  canManageCategories: boolean;
  onChange: (view: CatalogView) => void;
  view: CatalogView;
}) {
  const options: { label: string; value: CatalogView }[] = [
    { label: "Productos y cargos", value: "products" },
    { label: "Planes de membresía", value: "plans" },
    ...(canManageCategories ? [{ label: "Categorías", value: "categories" as CatalogView }] : []),
  ];
  return (
    <nav aria-label="Secciones del catálogo" className="flex flex-wrap gap-2 rounded-xl border border-[#d8ddd2] bg-white p-2 shadow-sm">
      {options.map((option) => (
        <button key={option.value} type="button" onClick={() => onChange(option.value)}
          className={view === option.value ? "app-button h-10" : "app-button-secondary h-10"}>
          {option.label}
        </button>
      ))}
    </nav>
  );
}

function CatalogPagination({ onPageChange, page, totalPages }: { onPageChange: (page: number) => void; page: number; totalPages: number; }) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Paginación del catálogo" className="flex items-center justify-end gap-3">
      <button className="app-button-secondary" disabled={page <= 0} onClick={() => onPageChange(page - 1)} type="button">Anterior</button>
      <span className="text-sm text-[#5d6b63]">Página {page + 1} de {totalPages}</span>
      <button className="app-button-secondary" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)} type="button">Siguiente</button>
    </nav>
  );
}

function AuthRequired() {
  return <AuthRequiredCard message="El catálogo requiere una cuenta con permisos para consultar o administrar productos y planes." title="Inicia sesión para ver el catálogo" />;
}
