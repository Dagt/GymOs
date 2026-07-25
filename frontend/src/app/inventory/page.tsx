"use client";

import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  InventoryAlertsPanel,
  InventoryMovementDrawer,
  InventoryMovementsTable,
  InventoryMovementSummary,
  InventoryPhysicalCountDrawer,
  InventoryStatusMessages,
  InventoryStockPanel,
  InventorySummaryHeader,
  InventoryTransferDrawer,
} from "./InventorySections";
import { useInventoryController } from "@/features/inventory/hooks/useInventoryController";

export default function InventoryPage() {
  const controller = useInventoryController();

  if (controller.loading) return <LoadingScreen text="Cargando inventario..." />;
  if (!controller.session) return <AuthRequiredCard message="Necesitas iniciar sesion para administrar inventario." title="Inicia sesion" />;

  return (
    <AppShell branchName={controller.activeBranch?.name} eyebrow="Operación"
      mode={controller.activeBranch ? "operation" : undefined}
      organizationName={controller.organization?.brandName || controller.organization?.tradeName}
      session={controller.session} subtitle="Existencias, movimientos, conteos físicos, transferencias y valuación." title="Inventario">
      <div className="grid gap-6">
        <InventorySummaryHeader
          branchSettings={controller.branchSettings}
          saving={controller.saving}
          summary={controller.summary}
          onToggleAllowNegativeStock={controller.toggleAllowNegativeStock}
        />

        <section className="grid gap-6 lg:grid-cols-[1fr_1.25fr]">
          <aside className="grid gap-6">
            <InventoryStockPanel loading={controller.stockLoading} onExportCsv={controller.exportStockCsv} onPageChange={controller.setStockPage}
              onQueryChange={controller.setStockQuery} onRefresh={() => controller.refresh()}
              onSelectProduct={controller.selectProduct} onSortChange={controller.setStockSort}
              page={controller.stockPage} query={controller.stockQuery}
              selectedProductId={controller.selectedProductId} sort={controller.stockSort}
              stock={controller.stock} total={controller.stockTotal} totalPages={controller.stockTotalPages} />
            <InventoryAlertsPanel lowStock={controller.lowStock} />
          </aside>

          <section className="grid gap-6">
            <InventoryStatusMessages error={controller.error} message={controller.message} />
            <InventoryMovementSummary
              onOpenDrawer={() => controller.setShowMovementDrawer(true)}
              onOpenPhysicalCountDrawer={() => controller.setShowPhysicalCountDrawer(true)}
              onOpenTransferDrawer={() => controller.setShowTransferDrawer(true)}
              selectedStock={controller.selectedStock}
            />
            <InventoryMovementsTable loading={controller.movementLoading} movements={controller.movements}
              onExportCsv={controller.exportMovementsCsv} onPageChange={controller.setMovementPage} page={controller.movementPage}
              totalPages={controller.movementTotalPages} />
          </section>
        </section>
      </div>

      <InventoryMovementDrawer movementType={controller.movementType}
        onClose={() => controller.setShowMovementDrawer(false)}
        onMovementTypeChange={controller.setMovementType} onProductChange={controller.selectDrawerProduct}
        onQuantityChange={controller.setQuantity} onReasonChange={controller.setReason}
        onReorderPointChange={controller.setReorderPoint} onSubmit={controller.submitMovement}
        onUnitCostChange={controller.setUnitCostCents} open={controller.showMovementDrawer}
        quantity={controller.quantity} reason={controller.reason} reorderPoint={controller.reorderPoint}
        saving={controller.saving} selectedProductId={controller.selectedProductId}
        stock={controller.stock} unitCostCents={controller.unitCostCents} />

      <InventoryPhysicalCountDrawer
        counts={controller.physicalCounts}
        open={controller.showPhysicalCountDrawer}
        saving={controller.saving}
        onApproveCount={controller.approveCount}
        onCancelCount={controller.cancelCount}
        onClose={() => controller.setShowPhysicalCountDrawer(false)}
        onSaveCountItem={controller.savePhysicalCountItem}
        onStartCount={controller.startPhysicalCount}
      />

      <InventoryTransferDrawer
        branches={controller.branches}
        open={controller.showTransferDrawer}
        saving={controller.saving}
        stock={controller.stock}
        transfers={controller.transfers}
        onCancelTransfer={controller.handleCancelTransfer}
        onClose={() => controller.setShowTransferDrawer(false)}
        onCreateTransfer={controller.handleCreateTransfer}
        onReceiveTransfer={controller.handleReceiveTransfer}
        onShipTransfer={controller.handleShipTransfer}
      />
    </AppShell>
  );
}
