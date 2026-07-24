package com.gymos.inventory.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.inventory.application.command.InventoryMovementCommand;
import com.gymos.inventory.application.port.InventoryCommandPort;
import com.gymos.inventory.application.port.InventoryQueryPort;
import com.gymos.inventory.application.query.InventoryMovementPageView;
import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import com.gymos.inventory.application.query.InventoryMovementView;
import com.gymos.inventory.application.query.InventoryProductView;
import com.gymos.inventory.application.query.InventoryStockLedgerView;
import com.gymos.inventory.application.query.InventoryStockPageView;
import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import com.gymos.inventory.application.query.InventoryStockView;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
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
		if (quantityAfter < 0) throw new BusinessException("INVENTORY_NEGATIVE_STOCK", "El movimiento dejaria existencias negativas.");
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

	private InventoryProductView ensureActiveInventoryProduct(UUID organizationId, UUID branchId, UUID productId) {
		InventoryProductView product = ensureTrackedProduct(organizationId, branchId, productId);
		if (!"ACTIVE".equals(product.status())) {
			throw new BusinessException("PRODUCT_NOT_INVENTORY_TRACKED", "El producto debe estar activo para registrar movimientos.");
		}
		return product;
	}

	private InventoryProductView ensureTrackedProduct(UUID organizationId, UUID branchId, UUID productId) {
		InventoryProductView product = queries.product(organizationId, branchId, productId)
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
			case "INITIAL", "PURCHASE", "ADJUSTMENT_IN", "RETURN" -> quantity;
			case "ADJUSTMENT_OUT", "WASTE" -> -quantity;
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

	private UUID safeCorrelationId(String correlationId) {
		try { return UUID.fromString(correlationId); }
		catch (RuntimeException exception) { return UUID.nameUUIDFromBytes(("gymos:" + correlationId).getBytes(StandardCharsets.UTF_8)); }
	}
	private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		if (principal.branchId() == null) throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
	}
}
