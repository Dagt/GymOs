package com.gymos.inventory.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.inventory.application.command.InventoryMovementCommand;
import com.gymos.inventory.application.port.InventoryCommandPort;
import com.gymos.inventory.application.port.InventoryQueryPort;
import com.gymos.inventory.application.query.InventoryBranchSettingsView;
import com.gymos.inventory.application.query.InventoryMovementPageView;
import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import com.gymos.inventory.application.query.InventoryMovementView;
import com.gymos.inventory.application.query.InventoryPhysicalCountView;
import com.gymos.inventory.application.query.InventoryProductView;
import com.gymos.inventory.application.query.InventoryStockLedgerView;
import com.gymos.inventory.application.query.InventoryStockPageView;
import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import com.gymos.inventory.application.query.InventoryStockView;
import com.gymos.inventory.application.query.InventorySummaryView;
import com.gymos.inventory.application.query.InventoryTransferView;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
	private final InventoryQueryPort queries;
	private final InventoryCommandPort commands;
	private final AuditService auditService;

	public InventoryService(InventoryQueryPort queries, InventoryCommandPort commands, AuditService auditService) {
		this.queries = queries;
		this.commands = commands;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<InventoryStockView> stock(GymOsPrincipal principal) {
		requireBranch(principal);
		return queries.stock(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public InventoryStockPageView searchStock(GymOsPrincipal principal, InventoryStockSearchCriteria criteria) {
		requireBranch(principal);
		return queries.searchStock(principal.organizationId(), principal.branchId(), criteria);
	}

	@Transactional(readOnly = true)
	public InventoryStockView stockDetail(GymOsPrincipal principal, UUID productId) {
		requireBranch(principal);
		ensureTrackedProduct(principal.organizationId(), principal.branchId(), productId);
		return queries.stockDetail(principal.organizationId(), principal.branchId(), productId)
				.orElseThrow(() -> new BusinessException("INVENTORY_PRODUCT_NOT_FOUND", "El producto no esta disponible para inventario."));
	}

	@Transactional(readOnly = true)
	public List<InventoryStockView> lowStock(GymOsPrincipal principal) {
		requireBranch(principal);
		return queries.lowStock(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public List<InventoryMovementView> movements(GymOsPrincipal principal, UUID productId, int limit) {
		requireBranch(principal);
		return queries.movements(principal.organizationId(), principal.branchId(), productId, Math.max(1, Math.min(limit, 200)));
	}

	@Transactional(readOnly = true)
	public InventoryMovementPageView searchMovements(GymOsPrincipal principal, InventoryMovementSearchCriteria criteria) {
		requireBranch(principal);
		return queries.searchMovements(principal.organizationId(), principal.branchId(), criteria);
	}

	@Transactional(readOnly = true)
	public InventorySummaryView summary(GymOsPrincipal principal) {
		requireBranch(principal);
		return queries.summary(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public InventoryBranchSettingsView branchSettings(GymOsPrincipal principal) {
		requireBranch(principal);
		return queries.branchSettings(principal.organizationId(), principal.branchId());
	}

	@Transactional
	public InventoryBranchSettingsView updateBranchSettings(GymOsPrincipal principal, boolean allowNegativeStock, String correlationId) {
		requireBranch(principal);
		commands.updateBranchSettings(principal.organizationId(), principal.branchId(), allowNegativeStock, principal.userId(), OffsetDateTime.now());
		auditService.record(principal, "INVENTORY_BRANCH_SETTINGS_UPDATED", "BRANCH", principal.branchId(), "BACKOFFICE", "allowNegativeStock=" + allowNegativeStock, correlationId);
		return queries.branchSettings(principal.organizationId(), principal.branchId());
	}

	@Transactional
	public InventoryStockView createMovement(GymOsPrincipal principal, InventoryMovementCommand request, String correlationId) {
		requireBranch(principal);
		InventoryProductView product = ensureActiveInventoryProduct(principal.organizationId(), principal.branchId(), request.productId());
		String movementType = normalizeManualMovementType(request.movementType());
		int quantityDelta = quantityDelta(movementType, request.quantity());
		OffsetDateTime now = OffsetDateTime.now();
		UUID stockId = commands.ensureStock(principal.organizationId(), principal.branchId(), product.id(), principal.userId(), now);
		InventoryStockLedgerView current = commands.lock(principal.organizationId(), principal.branchId(), stockId)
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible bloquear existencias."));
		int quantityAfter = current.quantityOnHand() + quantityDelta;
		boolean allowNegative = queries.isNegativeStockAllowed(principal.organizationId(), principal.branchId());
		if (quantityAfter < 0 && !allowNegative) {
			throw new BusinessException("INVENTORY_NEGATIVE_STOCK", "El movimiento dejaria existencias negativas.");
		}
		Long averageCost = nextAverageCost(current, quantityDelta, request.unitCostCents());
		commands.update(stockId, quantityAfter, current.reorderPoint(), averageCost, principal.userId(), now);
		UUID movementId = commands.createMovement(principal.organizationId(), principal.branchId(), stockId, product.id(), movementType,
				quantityDelta, current.quantityOnHand(), quantityAfter, request.unitCostCents(), blankToNull(request.referenceType()),
				request.referenceId(), request.reason(), now, principal.userId(), safeCorrelationId(correlationId));
		auditService.record(principal, "INVENTORY_MOVEMENT_CREATED", "INVENTORY_MOVEMENT", movementId, "BACKOFFICE", request.reason(), correlationId);
		return queries.stockDetail(principal.organizationId(), principal.branchId(), product.id())
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible recuperar existencias."));
	}

	@Transactional
	public InventoryStockView updateReorderPoint(GymOsPrincipal principal, UUID productId, int reorderPoint, String correlationId) {
		requireBranch(principal);
		ensureTrackedProduct(principal.organizationId(), principal.branchId(), productId);
		if (reorderPoint < 0) throw new BusinessException("INVENTORY_REORDER_POINT_INVALID", "El punto de reorden no puede ser negativo.");
		commands.updateReorderPoint(principal.organizationId(), principal.branchId(), productId, reorderPoint, principal.userId(), OffsetDateTime.now());
		auditService.record(principal, "INVENTORY_REORDER_POINT_UPDATED", "CATALOG_PRODUCT", productId, "BACKOFFICE", String.valueOf(reorderPoint), correlationId);
		return queries.stockDetail(principal.organizationId(), principal.branchId(), productId)
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible recuperar existencias."));
	}

	// Conteos Físicos (Auditoría de Stock)
	@Transactional(readOnly = true)
	public List<InventoryPhysicalCountView> physicalCounts(GymOsPrincipal principal) {
		requireBranch(principal);
		return queries.physicalCounts(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public InventoryPhysicalCountView physicalCountDetail(GymOsPrincipal principal, UUID countId) {
		requireBranch(principal);
		return queries.physicalCountDetail(principal.organizationId(), principal.branchId(), countId)
				.orElseThrow(() -> new BusinessException("INVENTORY_PHYSICAL_COUNT_NOT_FOUND", "El conteo fisico no existe."));
	}

	@Transactional
	public InventoryPhysicalCountView createPhysicalCount(GymOsPrincipal principal, String notes, String correlationId) {
		requireBranch(principal);
		OffsetDateTime now = OffsetDateTime.now();
		UUID countId = commands.createPhysicalCount(principal.organizationId(), principal.branchId(), notes, principal.userId(), now);
		List<InventoryStockView> trackedStock = queries.stock(principal.organizationId(), principal.branchId());
		for (InventoryStockView stockItem : trackedStock) {
			commands.updatePhysicalCountItem(countId, stockItem.productId(), stockItem.quantityOnHand(), null, stockItem.averageCostCents(), null, now);
		}
		auditService.record(principal, "INVENTORY_PHYSICAL_COUNT_CREATED", "INVENTORY_PHYSICAL_COUNT", countId, "BACKOFFICE", notes, correlationId);
		return physicalCountDetail(principal, countId);
	}

	@Transactional
	public InventoryPhysicalCountView updatePhysicalCountItem(GymOsPrincipal principal, UUID countId, UUID productId, Integer countedQuantity, String notes, String correlationId) {
		requireBranch(principal);
		InventoryPhysicalCountView count = physicalCountDetail(principal, countId);
		if (!"IN_PROGRESS".equals(count.status())) {
			throw new BusinessException("INVENTORY_COUNT_NOT_MUTABLE", "Solo se pueden actualizar conteos en progreso.");
		}
		InventoryStockView stockItem = queries.stockDetail(principal.organizationId(), principal.branchId(), productId)
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Producto no encontrado."));
		commands.updatePhysicalCountItem(countId, productId, stockItem.quantityOnHand(), countedQuantity, stockItem.averageCostCents(), notes, OffsetDateTime.now());
		return physicalCountDetail(principal, countId);
	}

	@Transactional
	public InventoryPhysicalCountView approvePhysicalCount(GymOsPrincipal principal, UUID countId, String correlationId) {
		requireBranch(principal);
		InventoryPhysicalCountView count = physicalCountDetail(principal, countId);
		if (!"IN_PROGRESS".equals(count.status()) && !"PENDING_APPROVAL".equals(count.status())) {
			throw new BusinessException("INVENTORY_COUNT_NOT_APPROVABLE", "El conteo no esta pendiente de aprobacion.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		for (var item : count.items()) {
			if (item.countedQuantity() != null && item.varianceQuantity() != null && item.varianceQuantity() != 0) {
				int delta = item.varianceQuantity();
				String movementType = delta > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT";
				int quantity = Math.abs(delta);
				InventoryMovementCommand command = new InventoryMovementCommand(
						item.productId(), movementType, quantity, item.unitCostCents(),
						"PHYSICAL_COUNT", countId, "Ajuste por conteo fisico de inventario"
				);
				createMovement(principal, command, correlationId);
			}
		}
		commands.updatePhysicalCountStatus(principal.organizationId(), principal.branchId(), countId, "APPROVED", principal.userId(), now);
		auditService.record(principal, "INVENTORY_PHYSICAL_COUNT_APPROVED", "INVENTORY_PHYSICAL_COUNT", countId, "BACKOFFICE", "Conteo fisico aprobado", correlationId);
		return physicalCountDetail(principal, countId);
	}

	@Transactional
	public InventoryPhysicalCountView cancelPhysicalCount(GymOsPrincipal principal, UUID countId, String correlationId) {
		requireBranch(principal);
		InventoryPhysicalCountView count = physicalCountDetail(principal, countId);
		if ("APPROVED".equals(count.status())) {
			throw new BusinessException("INVENTORY_COUNT_ALREADY_APPROVED", "Un conteo aprobado no se puede cancelar.");
		}
		commands.updatePhysicalCountStatus(principal.organizationId(), principal.branchId(), countId, "CANCELLED", principal.userId(), OffsetDateTime.now());
		auditService.record(principal, "INVENTORY_PHYSICAL_COUNT_CANCELLED", "INVENTORY_PHYSICAL_COUNT", countId, "BACKOFFICE", "Conteo fisico cancelado", correlationId);
		return physicalCountDetail(principal, countId);
	}

	// Transferencias entre Sucursales
	@Transactional(readOnly = true)
	public List<InventoryTransferView> transfers(GymOsPrincipal principal) {
		requireBranch(principal);
		return queries.transfers(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public InventoryTransferView transferDetail(GymOsPrincipal principal, UUID transferId) {
		requireBranch(principal);
		return queries.transferDetail(principal.organizationId(), principal.branchId(), transferId)
				.orElseThrow(() -> new BusinessException("INVENTORY_TRANSFER_NOT_FOUND", "La transferencia no existe."));
	}

	@Transactional
	public InventoryTransferView createTransfer(GymOsPrincipal principal, UUID destinationBranchId, String notes, List<TransferItemRequest> items, String correlationId) {
		requireBranch(principal);
		if (principal.branchId().equals(destinationBranchId)) {
			throw new BusinessException("INVENTORY_TRANSFER_SAME_BRANCH", "La sucursal destino debe ser distinta a la origen.");
		}
		if (items == null || items.isEmpty()) {
			throw new BusinessException("INVENTORY_TRANSFER_NO_ITEMS", "Debe incluir al menos un producto en la transferencia.");
		}
		boolean allowNegative = queries.isNegativeStockAllowed(principal.organizationId(), principal.branchId());
		OffsetDateTime now = OffsetDateTime.now();
		UUID transferId = commands.createTransfer(principal.organizationId(), principal.branchId(), destinationBranchId, notes, principal.userId(), now);
		for (TransferItemRequest itemReq : items) {
			InventoryStockView stockItem = queries.stockDetail(principal.organizationId(), principal.branchId(), itemReq.productId())
					.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Producto no encontrado en la sucursal de origen."));
			if (itemReq.requestedQuantity() > stockItem.quantityOnHand() && !allowNegative) {
				throw new BusinessException("INVENTORY_TRANSFER_EXCEEDS_STOCK",
						String.format("No hay suficiente existencia en la sucursal de origen para transferir %d unidades de '%s'. Existencia disponible: %d unidades.",
								itemReq.requestedQuantity(), stockItem.productName(), stockItem.quantityOnHand()));
			}
			commands.addTransferItem(transferId, itemReq.productId(), itemReq.requestedQuantity(), stockItem.averageCostCents());
		}
		auditService.record(principal, "INVENTORY_TRANSFER_CREATED", "INVENTORY_TRANSFER", transferId, "BACKOFFICE", notes, correlationId);
		return transferDetail(principal, transferId);
	}

	@Transactional
	public InventoryTransferView shipTransfer(GymOsPrincipal principal, UUID transferId, String correlationId) {
		requireBranch(principal);
		InventoryTransferView transfer = transferDetail(principal, transferId);
		if (!transfer.originBranchId().equals(principal.branchId())) {
			throw new BusinessException("INVENTORY_TRANSFER_NOT_ORIGIN", "Solo la sucursal de origen puede enviar la transferencia.");
		}
		if (!"REQUESTED".equals(transfer.status())) {
			throw new BusinessException("INVENTORY_TRANSFER_NOT_SHIPPABLE", "Solo transferencias solicitadas pueden enviarse.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		for (var item : transfer.items()) {
			int qty = item.requestedQuantity();
			InventoryMovementCommand cmd = new InventoryMovementCommand(
					item.productId(), "TRANSFER_OUT", qty, item.unitCostCents(),
					"TRANSFER", transferId, "Salida por transferencia a sucursal " + transfer.destinationBranchName()
			);
			createMovement(principal, cmd, correlationId);
			commands.updateTransferItemQuantities(transferId, item.productId(), qty, null);
		}
		commands.updateTransferStatus(principal.organizationId(), transferId, "SHIPPED", principal.userId(), now);
		auditService.record(principal, "INVENTORY_TRANSFER_SHIPPED", "INVENTORY_TRANSFER", transferId, "BACKOFFICE", "Transferencia enviada", correlationId);
		return transferDetail(principal, transferId);
	}

	@Transactional
	public InventoryTransferView receiveTransfer(GymOsPrincipal principal, UUID transferId, String correlationId) {
		requireBranch(principal);
		InventoryTransferView transfer = transferDetail(principal, transferId);
		if (!transfer.destinationBranchId().equals(principal.branchId())) {
			throw new BusinessException("INVENTORY_TRANSFER_NOT_DESTINATION", "Solo la sucursal de destino puede recibir la transferencia.");
		}
		if (!"SHIPPED".equals(transfer.status())) {
			throw new BusinessException("INVENTORY_TRANSFER_NOT_RECEIVABLE", "Solo transferencias enviadas pueden recibirse.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		for (var item : transfer.items()) {
			int qty = item.shippedQuantity() != null ? item.shippedQuantity() : item.requestedQuantity();
			InventoryMovementCommand cmd = new InventoryMovementCommand(
					item.productId(), "TRANSFER_IN", qty, item.unitCostCents(),
					"TRANSFER", transferId, "Entrada por transferencia desde sucursal " + transfer.originBranchName()
			);
			createMovement(principal, cmd, correlationId);
			commands.updateTransferItemQuantities(transferId, item.productId(), item.shippedQuantity(), qty);
		}
		commands.updateTransferStatus(principal.organizationId(), transferId, "RECEIVED", principal.userId(), now);
		auditService.record(principal, "INVENTORY_TRANSFER_RECEIVED", "INVENTORY_TRANSFER", transferId, "BACKOFFICE", "Transferencia recibida", correlationId);
		return transferDetail(principal, transferId);
	}

	@Transactional
	public InventoryTransferView cancelTransfer(GymOsPrincipal principal, UUID transferId, String correlationId) {
		requireBranch(principal);
		InventoryTransferView transfer = transferDetail(principal, transferId);
		if ("RECEIVED".equals(transfer.status())) {
			throw new BusinessException("INVENTORY_TRANSFER_ALREADY_RECEIVED", "Una transferencia recibida no se puede cancelar.");
		}
		if ("SHIPPED".equals(transfer.status())) {
			throw new BusinessException("INVENTORY_TRANSFER_ALREADY_SHIPPED", "Una transferencia enviada no se puede cancelar directamente.");
		}
		commands.updateTransferStatus(principal.organizationId(), transferId, "CANCELLED", principal.userId(), OffsetDateTime.now());
		auditService.record(principal, "INVENTORY_TRANSFER_CANCELLED", "INVENTORY_TRANSFER", transferId, "BACKOFFICE", "Transferencia cancelada", correlationId);
		return transferDetail(principal, transferId);
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_CONTEXT_REQUIRED", "Se requiere una sucursal activa para realizar esta operacion.");
		}
	}

	private InventoryProductView ensureActiveInventoryProduct(UUID organizationId, UUID branchId, UUID productId) {
		InventoryProductView product = ensureTrackedProduct(organizationId, branchId, productId);
		if (!"ACTIVE".equals(product.status())) {
			throw new BusinessException("PRODUCT_NOT_INVENTORY_TRACKED", "El producto debe estar activo para registrar movimientos.");
		}
		return product;
	}

	private InventoryProductView ensureTrackedProduct(UUID organizationId, UUID branchId, UUID productId) {
		Optional<InventoryProductView> productOpt = queries.product(organizationId, branchId, productId);
		if (productOpt.isEmpty()) {
			queries.product(organizationId, productId)
					.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe en la organizacion."));
			commands.attachBranch(organizationId, branchId, productId, null, OffsetDateTime.now());
			productOpt = queries.product(organizationId, branchId, productId);
		}
		InventoryProductView product = productOpt
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe en la sucursal activa."));
		if (!"PHYSICAL".equals(product.productType()) || !product.inventoryTracked()) {
			throw new BusinessException("PRODUCT_NOT_INVENTORY_TRACKED", "Solo los productos fisicos con control de inventario pueden manejar existencias.");
		}
		return product;
	}

	private String normalizeManualMovementType(String movementType) {
		String normalized = movementType == null ? "" : movementType.trim().toUpperCase();
		if ("SALE".equals(normalized)) throw new BusinessException("INVENTORY_SALE_MOVEMENT_RESERVED", "Las salidas por venta se generan automaticamente desde ventas.");
		return normalized;
	}

	private int quantityDelta(String movementType, int quantity) {
		if (quantity <= 0) throw new BusinessException("INVENTORY_QUANTITY_INVALID", "La cantidad debe ser mayor a cero.");
		return switch (movementType) {
			case "INITIAL", "PURCHASE", "ADJUSTMENT_IN", "RETURN", "TRANSFER_IN" -> quantity;
			case "ADJUSTMENT_OUT", "WASTE", "INTERNAL_CONSUMPTION", "TRANSFER_OUT" -> -quantity;
			default -> throw new BusinessException("INVENTORY_MOVEMENT_TYPE_INVALID", "Tipo de movimiento invalido.");
		};
	}

	private Long nextAverageCost(InventoryStockLedgerView current, int quantityDelta, Long unitCostCents) {
		if (quantityDelta <= 0 || unitCostCents == null) return current.averageCostCents();
		if (unitCostCents < 0) throw new BusinessException("INVENTORY_UNIT_COST_INVALID", "El costo unitario no puede ser negativo.");
		long currentCost = current.averageCostCents() == null ? unitCostCents : current.averageCostCents();
		long totalBefore = (long) current.quantityOnHand() * currentCost;
		long totalIncoming = (long) quantityDelta * unitCostCents;
		int quantityAfter = current.quantityOnHand() + quantityDelta;
		return quantityAfter == 0 ? unitCostCents : Math.round((double) (totalBefore + totalIncoming) / quantityAfter);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private UUID safeCorrelationId(String correlationId) {
		try { return UUID.fromString(correlationId); }
		catch (Exception ignored) { return UUID.nameUUIDFromBytes(correlationId == null ? new byte[0] : correlationId.getBytes(StandardCharsets.UTF_8)); }
	}

	public record TransferItemRequest(UUID productId, int requestedQuantity) {}
}
