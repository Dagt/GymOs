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

  const activeTransfersCount = controller.transfers.filter(
    (t) => t.status === "REQUESTED" || t.status === "SHIPPED"
  ).length;

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
          activeTransfersCount={activeTransfersCount}
          lowStockCount={controller.summary?.lowStockCount ?? 0}
          onChange={setView}
          view={view}
        />

        <InventoryStatusMessages error={controller.error} message={controller.message} />

        {view === "stock" && (
          <section className="grid gap-4 lg:grid-cols-12 lg:items-stretch">
            <div className="lg:col-span-7 flex flex-col">
              <InventoryStockPanel
                loading={controller.stockLoading}
                onExportCsv={controller.exportStockCsv}
                onPageChange={controller.setStockPage}
                onQueryChange={controller.setStockQuery}
                onRefresh={() => controller.refresh()}
                onSelectProduct={controller.selectProduct}
                onSortChange={controller.setStockSort}
                page={controller.stockPage}
                query={controller.stockQuery}
                selectedProductId={controller.selectedProductId}
                sort={controller.stockSort}
                stock={controller.stock}
                total={controller.stockTotal}
                totalPages={controller.stockTotalPages}
              />
            </div>
            <div className="lg:col-span-5 flex flex-col space-y-4">
              <InventoryMovementSummary
                selectedStock={controller.selectedStock}
                onOpenDrawer={() => controller.setShowMovementDrawer(true)}
                onOpenPhysicalCountDrawer={() => controller.setShowPhysicalCountDrawer(true)}
                onOpenTransferDrawer={() => controller.setShowTransferDrawer(true)}
              />
              <InventoryAlertsPanel
                lowStock={controller.lowStock}
                onSelectProduct={(product) => {
                  controller.selectProduct(product);
                }}
              />
            </div>
          </section>
        )}

        {view === "movements" && (
          <section className="space-y-4">
            <InventoryMovementsTable
              loading={controller.movementLoading}
              movements={controller.filteredMovements}
              onClearFilters={controller.clearMovementFilters}
              onExportCsv={controller.exportMovementsCsv}
              onPageChange={controller.setMovementPage}
              onProductIdFilterChange={controller.setMovementProductIdFilter}
              onQueryChange={controller.setMovementQuery}
              onTypeFilterChange={controller.setMovementTypeFilter}
              page={controller.movementPage}
              productIdFilter={controller.movementProductIdFilter}
              products={controller.stock}
              query={controller.movementQuery}
              totalPages={controller.movementTotalPages}
              typeFilter={controller.movementTypeFilter}
            />
          </section>
        )}

        {view === "transfers" && (
          <section className="space-y-4">
            <InventoryTransfersCard
              transfers={controller.transfers}
              onCancelTransfer={controller.handleCancelTransfer}
              onOpenDrawer={() => controller.setShowTransferDrawer(true)}
              onReceiveTransfer={controller.handleReceiveTransfer}
              onShipTransfer={controller.handleShipTransfer}
              saving={controller.saving}
            />
          </section>
        )}

        {view === "audits" && (
          <section className="space-y-4">
            <InventoryAuditsCard
              counts={controller.physicalCounts}
              onOpenDrawer={() => controller.setShowPhysicalCountDrawer(true)}
            />
          </section>
        )}
      </div>

      <InventoryMovementDrawer
        movementType={controller.movementType}
        onClose={() => controller.setShowMovementDrawer(false)}
        onMovementTypeChange={controller.setMovementType}
        onProductChange={controller.selectDrawerProduct}
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
        selectedProductId={controller.selectedProductId}
        stock={controller.stock}
        unitCostCents={controller.unitCostCents}
      />

      <InventoryPhysicalCountDrawer
        counts={controller.physicalCounts}
        onApproveCount={controller.approveCount}
        onCancelCount={controller.cancelCount}
        onClose={() => controller.setShowPhysicalCountDrawer(false)}
        onSaveCountItem={controller.savePhysicalCountItem}
        onStartCount={controller.startPhysicalCount}
        open={controller.showPhysicalCountDrawer}
        saving={controller.saving}
      />

      <InventoryTransferDrawer
        activeBranchId={controller.activeBranch?.id}
        branches={controller.branches}
        onClose={() => controller.setShowTransferDrawer(false)}
        onCreateTransfer={controller.handleCreateTransfer}
        open={controller.showTransferDrawer}
        saving={controller.saving}
        stock={controller.stock}
      />
    </AppShell>
  );
}
