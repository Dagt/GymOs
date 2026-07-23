"use client";

import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  InventoryAlertsPanel,
  InventoryMovementDrawer,
  InventoryMovementsTable,
  InventoryMovementSummary,
  InventoryStatusMessages,
  InventoryStockPanel,
} from "./InventorySections";
import { useInventoryController } from "@/features/inventory/hooks/useInventoryController";

export default function InventoryPage() {
  const controller = useInventoryController();

  if (controller.loading) return <LoadingScreen text="Cargando inventario..." />;
  if (!controller.session) return <AuthRequiredCard message="Necesitas iniciar sesion para administrar inventario." title="Inicia sesion" />;

  return (
    <AppShell branchName={controller.activeBranch?.name} eyebrow="Operacion"
      mode={controller.activeBranch ? "operation" : undefined}
      organizationName={controller.organization?.brandName || controller.organization?.tradeName}
      session={controller.session} subtitle="Existencias, movimientos, ajustes y alertas." title="Inventario">
      <section className="grid gap-6 lg:grid-cols-[1fr_1.25fr]">
        <aside className="grid gap-6">
          <InventoryStockPanel onRefresh={() => controller.refresh()} onSelectProduct={controller.selectProduct}
            selectedProductId={controller.selectedProductId} stock={controller.stock} />
          <InventoryAlertsPanel lowStock={controller.lowStock} />
        </aside>
        <section className="grid gap-6">
          <InventoryStatusMessages error={controller.error} message={controller.message} />
          <InventoryMovementSummary onOpenDrawer={() => controller.setShowMovementDrawer(true)}
            selectedStock={controller.selectedStock} />
          <InventoryMovementsTable movements={controller.movements} />
        </section>
      </section>
      <InventoryMovementDrawer movementType={controller.movementType}
        onClose={() => controller.setShowMovementDrawer(false)}
        onMovementTypeChange={controller.setMovementType} onProductChange={controller.selectDrawerProduct}
        onQuantityChange={controller.setQuantity} onReasonChange={controller.setReason}
        onReorderPointChange={controller.setReorderPoint} onSubmit={controller.submitMovement}
        onUnitCostChange={controller.setUnitCostCents} open={controller.showMovementDrawer}
        quantity={controller.quantity} reason={controller.reason} reorderPoint={controller.reorderPoint}
        saving={controller.saving} selectedProductId={controller.selectedProductId}
        stock={controller.stock} unitCostCents={controller.unitCostCents} />
    </AppShell>
  );
}
