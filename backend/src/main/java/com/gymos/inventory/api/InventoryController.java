package com.gymos.inventory.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.api.PageResponse;
import com.gymos.inventory.application.query.InventoryHistorySearchCriteria;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.inventory.application.InventoryService;
import com.gymos.inventory.application.command.InventoryMovementCommand;
import com.gymos.inventory.application.query.InventoryBranchSettingsView;
import com.gymos.inventory.application.query.InventoryMovementPageView;
import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import com.gymos.inventory.application.query.InventoryMovementView;
import com.gymos.inventory.application.query.InventoryPhysicalCountView;
import com.gymos.inventory.application.query.InventoryStockPageView;
import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import com.gymos.inventory.application.query.InventoryStockView;
import com.gymos.inventory.application.query.InventorySummaryView;
import com.gymos.inventory.application.query.InventoryTransferView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) { this.inventoryService = inventoryService; }

	@GetMapping
	List<InventoryStockView> stock(@AuthenticationPrincipal GymOsPrincipal principal) { return inventoryService.stock(principal); }

	@GetMapping("/summary")
	InventorySummaryView summary(@AuthenticationPrincipal GymOsPrincipal principal) { return inventoryService.summary(principal); }

	@GetMapping("/branch-settings")
	InventoryBranchSettingsView branchSettings(@AuthenticationPrincipal GymOsPrincipal principal) { return inventoryService.branchSettings(principal); }

	@PutMapping("/branch-settings")
	InventoryBranchSettingsView updateBranchSettings(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody UpdateBranchSettingsRequest request, HttpServletRequest servletRequest) {
		return inventoryService.updateBranchSettings(principal, request.allowNegativeStock(), correlationId(servletRequest));
	}

	@GetMapping("/search")
	InventoryStockPageView searchStock(@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "name,asc") String sort,
			@RequestParam(defaultValue = "false") boolean lowStockOnly) {
		return inventoryService.searchStock(principal, new InventoryStockSearchCriteria(q, page, size, sort, lowStockOnly));
	}

	@GetMapping("/{productId}")
	InventoryStockView stockDetail(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId) {
		return inventoryService.stockDetail(principal, productId);
	}

	@GetMapping("/low-stock")
	List<InventoryStockView> lowStock(@AuthenticationPrincipal GymOsPrincipal principal) { return inventoryService.lowStock(principal); }

	@GetMapping("/movements")
	List<InventoryMovementView> movements(@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) UUID productId, @RequestParam(defaultValue = "50") int limit) {
		return inventoryService.movements(principal, productId, limit);
	}

	@GetMapping("/movements/search")
	InventoryMovementPageView searchMovements(@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) UUID productId,
			@RequestParam(required = false) @Size(max = 200) String q,
			@RequestParam(required = false) @Pattern(regexp = "INITIAL|PURCHASE|ADJUSTMENT_IN|ADJUSTMENT_OUT|WASTE|INTERNAL_CONSUMPTION|RETURN|TRANSFER_OUT|TRANSFER_IN|SALE") String type,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
		return inventoryService.searchMovements(principal, new InventoryMovementSearchCriteria(productId, page, size, q, type));
	}

	@PostMapping("/movements")
	InventoryStockView createMovement(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody InventoryMovementRequest request, HttpServletRequest servletRequest) {
		return inventoryService.createMovement(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PutMapping("/products/{productId}/settings")
	InventoryStockView updateSettings(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID productId,
			@Valid @RequestBody InventorySettingsRequest request, HttpServletRequest servletRequest) {
		return inventoryService.updateReorderPoint(principal, productId, request.reorderPoint(), correlationId(servletRequest));
	}

	// Conteos Físicos
	@GetMapping("/physical-counts")
	List<InventoryPhysicalCountView> physicalCounts(@AuthenticationPrincipal GymOsPrincipal principal) {
		return inventoryService.physicalCounts(principal);
	}

	@GetMapping("/physical-counts/{countId}")
	InventoryPhysicalCountView physicalCountDetail(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID countId) {
		return inventoryService.physicalCountDetail(principal, countId);
	}

	@GetMapping("/physical-counts/search")
	PageResponse<InventoryPhysicalCountView> searchPhysicalCounts(@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "false") boolean activeOnly) {
		return inventoryService.searchPhysicalCounts(principal, new InventoryHistorySearchCriteria(page, size, activeOnly));
	}

	@PostMapping("/physical-counts")
	InventoryPhysicalCountView createPhysicalCount(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreatePhysicalCountRequest request, HttpServletRequest servletRequest) {
		return inventoryService.createPhysicalCount(principal, request.notes(), correlationId(servletRequest));
	}

	@PutMapping("/physical-counts/{countId}/items/{productId}")
	InventoryPhysicalCountView updatePhysicalCountItem(@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID countId, @PathVariable UUID productId,
			@Valid @RequestBody UpdatePhysicalCountItemRequest request, HttpServletRequest servletRequest) {
		return inventoryService.updatePhysicalCountItem(principal, countId, productId, request.countedQuantity(), request.notes(), correlationId(servletRequest));
	}

	@PostMapping("/physical-counts/{countId}/approve")
	InventoryPhysicalCountView approvePhysicalCount(@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID countId, HttpServletRequest servletRequest) {
		return inventoryService.approvePhysicalCount(principal, countId, correlationId(servletRequest));
	}

	@PostMapping("/physical-counts/{countId}/cancel")
	InventoryPhysicalCountView cancelPhysicalCount(@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID countId, HttpServletRequest servletRequest) {
		return inventoryService.cancelPhysicalCount(principal, countId, correlationId(servletRequest));
	}

	// Transferencias entre sucursales
	@GetMapping("/transfers")
	List<InventoryTransferView> transfers(@AuthenticationPrincipal GymOsPrincipal principal) {
		return inventoryService.transfers(principal);
	}

	@GetMapping("/transfers/{transferId}")
	InventoryTransferView transferDetail(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID transferId) {
		return inventoryService.transferDetail(principal, transferId);
	}

	@GetMapping("/transfers/search")
	PageResponse<InventoryTransferView> searchTransfers(@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "false") boolean activeOnly) {
		return inventoryService.searchTransfers(principal, new InventoryHistorySearchCriteria(page, size, activeOnly));
	}

	@PostMapping("/transfers")
	InventoryTransferView createTransfer(@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateTransferRequest request, HttpServletRequest servletRequest) {
		List<InventoryService.TransferItemRequest> items = request.items().stream()
				.map(item -> new InventoryService.TransferItemRequest(item.productId(), item.requestedQuantity()))
				.toList();
		return inventoryService.createTransfer(principal, request.destinationBranchId(), request.notes(), items, correlationId(servletRequest));
	}

	@PostMapping("/transfers/{transferId}/ship")
	InventoryTransferView shipTransfer(@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID transferId, HttpServletRequest servletRequest) {
		return inventoryService.shipTransfer(principal, transferId, correlationId(servletRequest));
	}

	@PostMapping("/transfers/{transferId}/receive")
	InventoryTransferView receiveTransfer(@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID transferId, HttpServletRequest servletRequest) {
		return inventoryService.receiveTransfer(principal, transferId, correlationId(servletRequest));
	}

	@PostMapping("/transfers/{transferId}/cancel")
	InventoryTransferView cancelTransfer(@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID transferId, HttpServletRequest servletRequest) {
		return inventoryService.cancelTransfer(principal, transferId, correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record InventoryMovementRequest(
			@NotNull UUID productId,
			@NotBlank @Pattern(regexp = "INITIAL|PURCHASE|ADJUSTMENT_IN|ADJUSTMENT_OUT|WASTE|INTERNAL_CONSUMPTION|RETURN|TRANSFER_OUT|TRANSFER_IN") String movementType,
			@Min(1) int quantity,
			@Min(0) Long unitCostCents,
			@Size(max = 80) String referenceType,
			UUID referenceId,
			@NotBlank @Size(max = 500) String reason
	) {
		private InventoryMovementCommand toCommand() {
			return new InventoryMovementCommand(productId, movementType, quantity, unitCostCents, referenceType, referenceId, reason);
		}
	}

	public record InventorySettingsRequest(@Min(0) int reorderPoint) {
	}

	public record UpdateBranchSettingsRequest(boolean allowNegativeStock) {
	}

	public record CreatePhysicalCountRequest(@Size(max = 500) String notes) {
	}

	public record UpdatePhysicalCountItemRequest(
			@Min(0) Integer countedQuantity,
			@Size(max = 500) String notes
	) {
	}

	public record CreateTransferRequest(
			@NotNull UUID destinationBranchId,
			@Size(max = 500) String notes,
			@NotEmpty List<@Valid TransferItemApiRequest> items
	) {
	}

	public record TransferItemApiRequest(
			@NotNull UUID productId,
			@Min(1) int requestedQuantity
	) {
	}
}
