package com.gymos.inventory.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.inventory.application.InventoryService;
import com.gymos.inventory.application.command.InventoryMovementCommand;
import com.gymos.inventory.infrastructure.InventoryRepository.InventoryMovementRecord;
import com.gymos.inventory.infrastructure.InventoryRepository.InventoryStockRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@GetMapping
	List<InventoryStockRecord> stock(@AuthenticationPrincipal GymOsPrincipal principal) {
		return inventoryService.stock(principal);
	}

	@GetMapping("/{productId}")
	InventoryStockRecord stockDetail(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId
	) {
		return inventoryService.stockDetail(principal, productId);
	}

	@GetMapping("/low-stock")
	List<InventoryStockRecord> lowStock(@AuthenticationPrincipal GymOsPrincipal principal) {
		return inventoryService.lowStock(principal);
	}

	@GetMapping("/movements")
	List<InventoryMovementRecord> movements(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) UUID productId,
			@RequestParam(defaultValue = "50") int limit
	) {
		return inventoryService.movements(principal, productId, limit);
	}

	@PostMapping("/movements")
	InventoryStockRecord createMovement(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody InventoryMovementRequest request,
			HttpServletRequest servletRequest
	) {
		return inventoryService.createMovement(principal, request.toCommand(), correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record InventoryMovementRequest(
			@NotNull UUID productId,
			@NotBlank @Pattern(regexp = "INITIAL|PURCHASE|ADJUSTMENT_IN|ADJUSTMENT_OUT|WASTE|RETURN")
			String movementType,
			@Min(1) int quantity,
			@Min(0) Long unitCostCents,
			@Min(0) Integer reorderPoint,
			@Size(max = 80) String referenceType,
			UUID referenceId,
			@NotBlank @Size(max = 500) String reason
	) {
		private InventoryMovementCommand toCommand() {
			return new InventoryMovementCommand(productId, movementType, quantity, unitCostCents, reorderPoint, referenceType, referenceId, reason);
		}
	}
}
