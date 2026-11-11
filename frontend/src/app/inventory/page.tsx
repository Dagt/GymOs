"use client";

import { useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  InventoryAlertsPanel,
  InventoryAuditsCard,
  InventoryMovementDrawer,
  InventoryMovementsTable,
  InventoryMovementSummary,
  InventoryNavigation,
  InventoryPhysicalCountDrawer,
  InventoryStatusMessages,
  InventoryStockPanel,
  InventorySummaryHeader,
  InventoryToolbar,
  InventoryTransferDrawer,
  InventoryTransfersCard,
  type InventoryView,
} from "./InventorySections";
import { useInventoryController } from "@/features/inventory/hooks/useInventoryController";

export default function InventoryPage() {
  const controller = useInventoryController();
  const [view, setView] = useState<InventoryView>("stock");

  if (controller.loading) return <LoadingScreen text="Cargando inventario..." />;
  if (!controller.session) return <AuthRequiredCard message="Necesitas iniciar sesión para administrar inventario." title="Inicia sesión" />;

  return (
    <AppShell
      branchName={controller.activeBranch?.name}
      eyebrow="Operación"
      mode={controller.activeBranch ? "operation" : undefined}
      organizationName={controller.organization?.brandName || controller.organization?.tradeName}
      session={controller.session}
      subtitle="Existencias, movimientos, conteos físicos, transferencias y valuación."
      title="Inventario"
    >
      <div className="mx-auto w-full max-w-7xl space-y-4">
        <InventoryToolbar
          branchSettings={controller.branchSettings}
          saving={controller.saving}
          onToggleAllowNegativeStock={controller.toggleAllowNegativeStock}
        />

        <InventorySummaryHeader summary={controller.summary} />

        <InventoryNavigation
          activeTransfersCount={controller.activeTransfersCount}
          lowStockCount={controller.summary?.lowStockCount ?? 0}
          onChange={setView}
          view={view}
        />

        <InventoryStatusMessages error={controller.error} message={controller.message} />

        {view === "stock" && (
          <section className="grid gap-4 lg:grid-cols-12 lg:items-start">
            <div className="lg:col-span-7 flex flex-col">
              {controller.stockList.error ? <InventoryListFailure title="Existencias" onRetry={controller.stockList.refresh} /> : <InventoryStockPanel
                loading={controller.stockList.loading}
                onExportCsv={controller.exportStockCsv}
                onPageChange={controller.stockList.setPage}
                onPageSizeChange={controller.stockList.setPageSize}
                onQueryChange={controller.setStockQuery}
                onRefresh={() => controller.refresh()}
                onSelectProduct={controller.selectProduct}
                onSortChange={controller.setStockSort}
                page={controller.stockList.page}
                pageSize={controller.stockList.size}
                query={controller.stockQuery}
                selectedProductId={controller.selectedProductId}
                sort={controller.stockList.filters.sort ?? "name,asc"}
                stock={controller.stockList.items}
                total={controller.stockList.total}
              />}
            </div>
            <div className="lg:col-span-5 flex flex-col space-y-4">
              <InventoryMovementSummary
                selectedStock={controller.selectedStock}
                onOpenDrawer={() => controller.setShowMovementDrawer(true)}
              />
              {controller.lowStockList.error ? <InventoryListFailure title="Alertas de stock" onRetry={controller.lowStockList.refresh} /> : <InventoryAlertsPanel
                lowStock={controller.lowStockList.items}
                page={controller.lowStockList.page}
                pageSize={controller.lowStockList.size}
                total={controller.lowStockList.total}
                loading={controller.lowStockList.loading}
                onPageChange={controller.lowStockList.setPage}
                onSelectProduct={(product) => {
                  controller.selectProduct(product);
                }}
              />}
            </div>
          </section>
        )}

        {view === "movements" && (
          <section className="space-y-4">
            {controller.movementList.error ? <InventoryListFailure title="Historial de movimientos" onRetry={controller.movementList.refresh} /> : <InventoryMovementsTable
              accessToken={controller.session.accessToken}
              loading={controller.movementList.loading}
              movements={controller.movementList.items}
              onClearFilters={controller.clearMovementFilters}
              onExportCsv={controller.exportMovementsCsv}
              onPageChange={controller.movementList.setPage}
              onPageSizeChange={controller.movementList.setPageSize}
              onProductChange={controller.selectMovementFilter}
              onQueryChange={controller.setMovementQuery}
              onTypeFilterChange={controller.setMovementTypeFilter}
              page={controller.movementList.page}
              pageSize={controller.movementList.size}
              product={controller.movementProduct}
              query={controller.movementQuery}
              total={controller.movementList.total}
              typeFilter={controller.movementList.filters.type ?? ""}
            />}
          </section>
        )}

        {view === "transfers" && (
          <section className="space-y-4">
            {controller.transferList.error ? <InventoryListFailure title="Historial de transferencias" onRetry={controller.transferList.refresh} /> : <InventoryTransfersCard
              transfers={controller.transferList.items}
              activeTransfersCount={controller.activeTransfersCount}
              page={controller.transferList.page}
              pageSize={controller.transferList.size}
              total={controller.transferList.total}
              loading={controller.transferList.loading}
              onPageChange={controller.transferList.setPage}
              onPageSizeChange={controller.transferList.setPageSize}
              onCancelTransfer={controller.handleCancelTransfer}
              onOpenDrawer={() => controller.setShowTransferDrawer(true)}
              onReceiveTransfer={controller.handleReceiveTransfer}
              onShipTransfer={controller.handleShipTransfer}
              saving={controller.saving}
            />}
          </section>
        )}

        {view === "audits" && (
          <section className="space-y-4">
            {controller.countList.error ? <InventoryListFailure title="Historial de conteos" onRetry={controller.countList.refresh} /> : <InventoryAuditsCard
              counts={controller.countList.items}
              activeCount={controller.activeCount}
              page={controller.countList.page}
              pageSize={controller.countList.size}
              total={controller.countList.total}
              loading={controller.countList.loading}
              onPageChange={controller.countList.setPage}
              onPageSizeChange={controller.countList.setPageSize}
              onOpenDrawer={() => controller.setShowPhysicalCountDrawer(true)}
            />}
          </section>
        )}
      </div>

      <InventoryMovementDrawer
        accessToken={controller.session.accessToken}
        movementType={controller.movementType}
        onClose={() => controller.setShowMovementDrawer(false)}
        onMovementTypeChange={controller.setMovementType}
        onProductChange={controller.selectProduct}
        onQuantityChange={controller.setQuantity}
        onReasonChange={controller.setReason}
        onReorderPointChange={controller.setReorderPoint}
        onSubmit={controller.submitMovement}
        onUnitCostChange={controller.setUnitCostCents}
        open={controller.showMovementDrawer}
        quantity={controller.quantity}
        reason={controller.reason}
        reorderPoint={controller.reorderPoint}
        saving={controller.saving}
        selectedProduct={controller.selectedStock}
        unitCostCents={controller.unitCostCents}
      />

      <InventoryPhysicalCountDrawer
        activeCount={controller.activeCount}
        loading={controller.activeCountLoading}
        onApproveCount={controller.approveCount}
        onCancelCount={controller.cancelCount}
        onClose={() => controller.setShowPhysicalCountDrawer(false)}
        onSaveCountItem={controller.savePhysicalCountItem}
        onStartCount={controller.startPhysicalCount}
        open={controller.showPhysicalCountDrawer}
        saving={controller.saving}
      />

      <InventoryTransferDrawer
        accessToken={controller.session.accessToken}
        activeBranchId={controller.activeBranch?.id}
        branches={controller.branches}
        onClose={() => controller.setShowTransferDrawer(false)}
        onCreateTransfer={controller.handleCreateTransfer}
        open={controller.showTransferDrawer}
        saving={controller.saving}
      />
    </AppShell>
  );
}

function InventoryListFailure({ title, onRetry }: { title: string; onRetry: () => void }) {
  return <section className="app-card p-5" aria-label={title}>
    <h2 className="app-card-title">{title}</h2>
    <p className="mt-2 text-sm text-[var(--brand-muted)]">No fue posible cargar este listado. El total no está disponible; esto no significa que no existan registros.</p>
    <button className="app-button-secondary mt-3" type="button" onClick={onRetry}>Reintentar listado</button>
  </section>;
}
