"use client";

import { DEFAULT_PAGE_SIZE } from "@/lib/pagination";

import { Suspense, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { PlanDrawer, PlanList, ProductActions } from "./CatalogSections";
import { useCatalogController } from "./useCatalogController";
import { CatalogFilters } from "@/features/catalog/components/CatalogFilters";
import { CatalogProductDrawer } from "@/features/catalog/components/CatalogProductDrawer";
import { CatalogProductTable } from "@/features/catalog/components/CatalogProductTable";
import { CatalogVariantBatchDrawer } from "@/features/catalog/components/CatalogVariantBatchDrawer";
import { CatalogSummaryCards } from "@/features/catalog/components/CatalogSummaryCards";
import { CatalogCategoryManager } from "@/features/catalog/components/CatalogCategoryManager";
import { VisitPackagePlansSection } from "@/features/catalog/components/VisitPackagePlansSection";
import { Pagination } from "@/components/Pagination";

type CatalogView = "products" | "plans" | "visit-packages" | "categories";

export default function CatalogPage() {
  return <Suspense fallback={<LoadingScreen text="Cargando catálogo..." />}><CatalogContent /></Suspense>;
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

  function openVariantCreation() {
    setView("products");
    controller.openVariantCreation();
  }

  return (
    <AppShell eyebrow="Catálogo comercial" session={controller.session} subtitle="Artículos, planes de membresía y paquetes de visitas de la sucursal activa." title="Catálogo">
      {controller.error ? <InlineError message={controller.error} /> : null}
      {controller.message ? <p className="rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] px-3 py-2 text-sm text-[#31563c]">{controller.message}</p> : null}
      <section className="grid gap-4">
        <CatalogNavigation canManageCategories={controller.canManageCategories} view={view} onChange={setView} />
        {view === "products" ? (
          <section className="grid gap-3">
            <ProductActions canCreate={controller.canCreate} onNewProduct={openProductCreation} onNewVariants={openVariantCreation} />
            <div className="grid gap-3 xl:grid-cols-[260px_minmax(0,1fr)] xl:items-stretch">
              <CatalogSummaryCards summary={controller.summary} />
              <CatalogFilters categories={controller.categories} filters={controller.catalogFilters.filters}
                searchInput={controller.catalogFilters.searchInput} total={controller.totalProducts}
                onClear={controller.catalogFilters.clear} onSearchChange={controller.catalogFilters.setSearchInput}
                onUpdate={controller.catalogFilters.update} />
            </div>
            <Pagination label="artículos" page={controller.catalogFilters.filters.page ?? 0}
              size={controller.catalogFilters.filters.size ?? DEFAULT_PAGE_SIZE} totalElements={controller.totalProducts}
              loading={controller.loading || controller.saving}
              onPageChange={(page) => controller.catalogFilters.update("page", String(page))}
              onPageSizeChange={(size) => controller.catalogFilters.update("size", String(size))} />
            <CatalogProductTable canArchive={controller.canArchive} canManageStatus={controller.canManageStatus}
              canCreate={controller.canCreate} canUpdate={controller.canUpdate} loading={controller.loading} products={controller.products}
              families={controller.families}
              saving={controller.saving} onArchive={controller.archiveCatalogProduct} onEdit={controller.openProductEdition}
              onAddVariant={(product) => controller.openVariantCreation(product.familyId)}
              onRestore={controller.restoreCatalogProduct} onToggleStatus={controller.toggleProductStatus} />
          </section>
        ) : null}
        {view === "plans" ? <PlanList canCreate={controller.canCreate} canUpdate={controller.canUpdate} plans={controller.plans} onCreatePlan={openPlanCreation} onEditPlan={controller.openPlanEdition} /> : null}
        {view === "visit-packages" ? <VisitPackagePlansSection accessToken={controller.session.accessToken} currentBranchId={controller.session.user.branchId} canCreate={controller.canCreate} canUpdate={controller.canUpdate} /> : null}
        {view === "categories" && controller.canManageCategories ? <CatalogCategoryManager accessToken={controller.session.accessToken} categories={controller.categories} onChanged={controller.reloadCatalog} /> : null}
      </section>
      <CatalogProductDrawer
        categories={controller.categories}
        canManageImages={controller.canManageImages}
        canManageInventory={controller.canManageInventory}
        canReadCosts={controller.canReadCosts}
        editing={Boolean(controller.editingProductId)}
        editingVariant={controller.editingProductIsVariant}
        existingImage={Boolean(controller.existingImageObjectKey)}
        form={controller.form}
        open={controller.showProductDrawer}
        price={controller.price}
        reorderPoint={controller.reorderPoint}
        tagsText={controller.tagsText}
        image={controller.productImage}
        saving={controller.saving}
        onClose={controller.resetProductDrawer}
        onFormChange={controller.setForm}
        onPriceChange={controller.setPrice}
        onReorderPointChange={controller.setReorderPoint}
        onTagsTextChange={controller.setTagsText}
        onImageChange={controller.setProductImage}
        onRemoveImage={controller.removeCurrentProductImage}
        onSubmit={controller.submitProduct}
      />
      <CatalogVariantBatchDrawer
        key={controller.variantDrawerKey}
        categories={controller.categories}
        families={controller.families}
        canManageInventory={controller.canManageInventory}
        canReadCosts={controller.canReadCosts}
        initialFamilyId={controller.variantInitialFamilyId}
        open={controller.showVariantDrawer}
        saving={controller.saving}
        onClose={controller.resetVariantDrawer}
        onSubmit={controller.submitProductVariants}
      />
      <PlanDrawer editingPlanId={controller.editingPlanId} form={controller.planForm} open={controller.showPlanDrawer}
        saving={controller.saving} onClose={controller.resetPlanDrawer} onFormChange={controller.setPlanForm}
        onSubmit={controller.submitPlan} />
    </AppShell>
  );
}

function CatalogNavigation({ canManageCategories, onChange, view }: { canManageCategories: boolean; onChange: (view: CatalogView) => void; view: CatalogView }) {
  const options: { label: string; value: CatalogView }[] = [
    { label: "Artículos", value: "products" },
    { label: "Planes de membresía", value: "plans" },
    { label: "Paquetes de visitas", value: "visit-packages" },
    ...(canManageCategories ? [{ label: "Categorías", value: "categories" as CatalogView }] : []),
  ];
  return <nav aria-label="Secciones del catálogo" className="grid gap-2 rounded-xl border border-[var(--brand-line)] bg-white p-2 shadow-sm sm:grid-cols-2 xl:grid-cols-4">{options.map((option) => <button key={option.value} type="button" onClick={() => onChange(option.value)} className={view === option.value ? "app-button h-10 w-full" : "app-button-secondary h-10 w-full"}>{option.label}</button>)}</nav>;
}

function AuthRequired() {
  return <AuthRequiredCard message="Se requiere una cuenta autorizada." title="Inicia sesión para ver el catálogo" />;
}
