package com.gymos.inventory.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.inventory.api.InventoryController.InventoryMovementRequest;
import com.gymos.inventory.infrastructure.InventoryRepository;
import com.gymos.inventory.infrastructure.InventoryRepository.InventoryMovementRecord;
import com.gymos.inventory.infrastructure.InventoryRepository.InventoryStockRecord;
import com.gymos.inventory.infrastructure.InventoryRepository.ProductForInventory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
	private final JdbcClient jdbcClient;
	private final InventoryRepository inventoryRepository;
	private final AuditService auditService;

	public InventoryService(JdbcClient jdbcClient, InventoryRepository inventoryRepository, AuditService auditService) {
		this.jdbcClient = jdbcClient;
		this.inventoryRepository = inventoryRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<InventoryStockRecord> stock(GymOsPrincipal principal) {
		requireBranch(principal);
		return inventoryRepository.stock(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public InventoryStockRecord stockDetail(GymOsPrincipal principal, UUID productId) {
		requireBranch(principal);
		ensurePhysicalProduct(principal.organizationId(), productId);
		return inventoryRepository.stockDetail(principal.organizationId(), principal.branchId(), productId)
				.orElseThrow(() -> new BusinessException("INVENTORY_PRODUCT_NOT_FOUND", "El producto no esta disponible para inventario."));
	}

	@Transactional(readOnly = true)
	public List<InventoryStockRecord> lowStock(GymOsPrincipal principal) {
		requireBranch(principal);
		return inventoryRepository.lowStock(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public List<InventoryMovementRecord> movements(GymOsPrincipal principal, UUID productId, int limit) {
		requireBranch(principal);
		int boundedLimit = Math.max(1, Math.min(limit, 200));
		return inventoryRepository.movements(principal.organizationId(), principal.branchId(), productId, boundedLimit);
	}

	@Transactional
	public InventoryStockRecord createMovement(GymOsPrincipal principal, InventoryMovementRequest request, String correlationId) {
		requireBranch(principal);
		ProductForInventory product = ensurePhysicalProduct(principal.organizationId(), request.productId());
		String movementType = request.movementType().trim().toUpperCase();
		int quantityDelta = quantityDelta(movementType, request.quantity());
		OffsetDateTime now = OffsetDateTime.now();
		UUID stockId = ensureStock(principal, product.id(), now);
		InventoryStockRecord current = inventoryRepository.stockById(principal.organizationId(), principal.branchId(), stockId)
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible recuperar existencias."));
		int quantityAfter = current.quantityOnHand() + quantityDelta;
		if (quantityAfter < 0) {
			throw new BusinessException("INVENTORY_NEGATIVE_STOCK", "El movimiento dejaria existencias negativas.");
		}
		Long averageCost = nextAverageCost(current, quantityDelta, request.unitCostCents());
		int reorderPoint = request.reorderPoint() == null ? current.reorderPoint() : request.reorderPoint();

		jdbcClient.sql("""
				UPDATE inventory_stock
				SET quantity_on_hand = :quantityOnHand,
				    reorder_point = :reorderPoint,
				    average_cost_cents = :averageCostCents,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE id = :stockId
				""")
				.param("quantityOnHand", quantityAfter)
				.param("reorderPoint", reorderPoint)
				.param("averageCostCents", averageCost)
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("stockId", stockId)
				.update();

		UUID movementId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO inventory_movement (
				    id, organization_id, branch_id, stock_id, product_id, movement_type,
				    quantity_delta, quantity_before, quantity_after, unit_cost_cents,
				    reference_type, reference_id, reason, occurred_at, created_at, created_by, correlation_id
				)
				VALUES (
				    :id, :organizationId, :branchId, :stockId, :productId, :movementType,
				    :quantityDelta, :quantityBefore, :quantityAfter, :unitCostCents,
				    :referenceType, :referenceId, :reason, :occurredAt, :createdAt, :createdBy, :correlationId
				)
				""")
				.param("id", movementId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("stockId", stockId)
				.param("productId", product.id())
				.param("movementType", movementType)
				.param("quantityDelta", quantityDelta)
				.param("quantityBefore", current.quantityOnHand())
				.param("quantityAfter", quantityAfter)
				.param("unitCostCents", request.unitCostCents())
				.param("referenceType", blankToNull(request.referenceType()))
				.param("referenceId", request.referenceId())
				.param("reason", request.reason())
				.param("occurredAt", now)
				.param("createdAt", now)
				.param("createdBy", principal.userId())
				.param("correlationId", UUID.fromString(correlationId))
				.update();
		auditService.record(principal, "INVENTORY_MOVEMENT_CREATED", "INVENTORY_MOVEMENT", movementId, "BACKOFFICE", request.reason(), correlationId);
		return inventoryRepository.stockDetail(principal.organizationId(), principal.branchId(), product.id())
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible recuperar existencias."));
	}

	private UUID ensureStock(GymOsPrincipal principal, UUID productId, OffsetDateTime now) {
		return inventoryRepository.stockIdentity(principal.organizationId(), principal.branchId(), productId)
				.orElseGet(() -> {
					UUID stockId = UUID.randomUUID();
					jdbcClient.sql("""
							INSERT INTO inventory_stock (
							    id, organization_id, branch_id, product_id, quantity_on_hand, reorder_point,
							    created_at, updated_at, created_by, updated_by, version
							)
							VALUES (
							    :id, :organizationId, :branchId, :productId, 0, 0,
							    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
							)
							""")
							.param("id", stockId)
							.param("organizationId", principal.organizationId())
							.param("branchId", principal.branchId())
							.param("productId", productId)
							.param("createdAt", now)
							.param("updatedAt", now)
							.param("createdBy", principal.userId())
							.param("updatedBy", principal.userId())
							.update();
					return stockId;
				});
	}

	private ProductForInventory ensurePhysicalProduct(UUID organizationId, UUID productId) {
		ProductForInventory product = inventoryRepository.product(organizationId, productId)
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe o no esta disponible."));
		if (!"PHYSICAL".equals(product.productType()) || !"ACTIVE".equals(product.status())) {
			throw new BusinessException("PRODUCT_NOT_INVENTORY_TRACKED", "Solo los productos fisicos activos pueden manejar inventario.");
		}
		return product;
	}

	private int quantityDelta(String movementType, int quantity) {
		return switch (movementType) {
			case "INITIAL", "PURCHASE", "ADJUSTMENT_IN", "RETURN" -> quantity;
			case "ADJUSTMENT_OUT", "WASTE", "SALE" -> -quantity;
			default -> throw new BusinessException("INVENTORY_MOVEMENT_TYPE_INVALID", "Tipo de movimiento invalido.");
		};
	}

	private Long nextAverageCost(InventoryStockRecord current, int quantityDelta, Long unitCostCents) {
		if (quantityDelta <= 0 || unitCostCents == null) {
			return current.averageCostCents();
		}
		long currentCost = current.averageCostCents() == null ? unitCostCents : current.averageCostCents();
		long totalBefore = (long) current.quantityOnHand() * currentCost;
		long totalIncoming = (long) quantityDelta * unitCostCents;
		int quantityAfter = current.quantityOnHand() + quantityDelta;
		return quantityAfter == 0 ? unitCostCents : Math.round((double) (totalBefore + totalIncoming) / quantityAfter);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}
}
