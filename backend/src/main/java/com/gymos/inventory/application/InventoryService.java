package com.gymos.inventory.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.inventory.application.command.InventoryMovementCommand;
import com.gymos.inventory.infrastructure.InventoryRepository;
import com.gymos.inventory.infrastructure.InventoryRepository.InventoryMovementRecord;
import com.gymos.inventory.infrastructure.InventoryRepository.InventoryStockRecord;
import com.gymos.inventory.infrastructure.InventoryRepository.ProductForInventory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
	private final InventoryRepository inventoryRepository;
	private final AuditService auditService;

	public InventoryService(InventoryRepository inventoryRepository, AuditService auditService) {
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
	public InventoryStockRecord createMovement(GymOsPrincipal principal, InventoryMovementCommand request, String correlationId) {
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

		inventoryRepository.updateStock(stockId, quantityAfter, reorderPoint, averageCost, principal.userId(), now);

		UUID movementId = inventoryRepository.createMovement(
				principal.organizationId(),
				principal.branchId(),
				stockId,
				product.id(),
				movementType,
				quantityDelta,
				current.quantityOnHand(),
				quantityAfter,
				request.unitCostCents(),
				blankToNull(request.referenceType()),
				request.referenceId(),
				request.reason(),
				now,
				principal.userId(),
				UUID.fromString(correlationId)
		);
		auditService.record(principal, "INVENTORY_MOVEMENT_CREATED", "INVENTORY_MOVEMENT", movementId, "BACKOFFICE", request.reason(), correlationId);
		return inventoryRepository.stockDetail(principal.organizationId(), principal.branchId(), product.id())
				.orElseThrow(() -> new BusinessException("INVENTORY_STOCK_NOT_FOUND", "No fue posible recuperar existencias."));
	}

	private UUID ensureStock(GymOsPrincipal principal, UUID productId, OffsetDateTime now) {
		return inventoryRepository.stockIdentity(principal.organizationId(), principal.branchId(), productId)
				.orElseGet(() -> {
					return inventoryRepository.createStock(principal.organizationId(), principal.branchId(), productId, principal.userId(), now);
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
