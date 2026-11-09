package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.RefundItemCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.RefundableSaleItem;
import com.gymos.sales.model.SaleHeader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SalesRefundValidationService {
	private final SalesRepository salesRepository;
	private final SalesLookupService lookupService;
	private final SalesFinancialIntegrityGuard financialIntegrity;

	SalesRefundValidationService(SalesRepository salesRepository, SalesLookupService lookupService, SalesFinancialIntegrityGuard financialIntegrity) {
		this.salesRepository = salesRepository;
		this.lookupService = lookupService;
		this.financialIntegrity = financialIntegrity;
	}

	void lockSale(GymOsPrincipal principal, UUID saleId) {
		salesRepository.lockSaleForRefund(principal.organizationId(), saleId);
	}

	RefundValidationResult validate(GymOsPrincipal principal, UUID saleId, SaleHeader sale, RequestRefundCommand request) {
		if (request.amountCents() <= 0) {
			throw new BusinessException("REFUND_AMOUNT_INVALID", "El reembolso debe ser mayor a cero.");
		}
		financialIntegrity.guardRefund(principal, saleId, request);
		validatePaymentSelection(principal, saleId, request);
		long alreadyRefunded = salesRepository.processedRefundTotal(principal.organizationId(), saleId);
		long refundable = sale.paidCents() - alreadyRefunded;
		if (request.amountCents() > refundable) {
			throw new BusinessException("REFUND_EXCEEDS_REFUNDABLE_BALANCE", "El reembolso supera el saldo pagado disponible.");
		}
		boolean consumedVisitPackage = salesRepository.visitPackageHasConsumptions(principal.organizationId(), saleId);
		if (request.amountCents() == refundable && consumedVisitPackage) {
			throw new BusinessException(
					"VISIT_PACKAGE_CONSUMED_REFUND_REQUIRES_REVIEW",
					"El paquete ya tiene visitas consumidas. Revisa el saldo antes de procesar un reembolso total.");
		}
		if (request.itemized()) {
			if (salesRepository.hasAmountOnlyRefunds(principal.organizationId(), saleId)) {
				throw mixedMode();
			}
			return new RefundValidationResult(
					alreadyRefunded,
					validateItems(principal.organizationId(), saleId, request.items(), consumedVisitPackage));
		}
		if (salesRepository.hasItemizedRefunds(principal.organizationId(), saleId)) {
			throw mixedMode();
		}
		return new RefundValidationResult(alreadyRefunded, List.of());
	}

	private List<ValidatedRefundItem> validateItems(
			UUID organizationId, UUID saleId, List<RefundItemCommand> requestedItems,
			boolean consumedVisitPackage
	) {
		Map<UUID, RefundableSaleItem> available = new HashMap<>();
		for (RefundableSaleItem item : salesRepository.refundableSaleItems(organizationId, saleId)) {
			available.put(item.saleItemId(), item);
		}
		Set<UUID> seen = new HashSet<>();
		List<ValidatedRefundItem> validated = new ArrayList<>();
		for (RefundItemCommand requested : requestedItems) {
			if (requested == null || requested.saleItemId() == null) {
				throw new BusinessException("REFUND_ITEM_REQUIRED", "Selecciona un producto de la venta para devolver.");
			}
			if (!seen.add(requested.saleItemId())) {
				throw new BusinessException("REFUND_ITEM_DUPLICATED", "No repitas el mismo producto dentro del reembolso.");
			}
			if (requested.quantity() <= 0) {
				throw new BusinessException("REFUND_ITEM_QUANTITY_INVALID", "La cantidad devuelta debe ser mayor a cero.");
			}
			RefundableSaleItem saleItem = available.get(requested.saleItemId());
			if (saleItem == null) {
				throw new BusinessException("REFUND_ITEM_NOT_FOUND", "El producto no pertenece a la venta o ya no esta disponible.");
			}
			if (consumedVisitPackage && "VISIT_PACKAGE".equals(saleItem.itemType())) {
				throw new BusinessException(
						"VISIT_PACKAGE_CONSUMED_REFUND_REQUIRES_REVIEW",
						"El paquete ya tiene visitas consumidas. Su reembolso requiere revisión administrativa.");
			}
			if (requested.quantity() > saleItem.refundableQuantity()) {
				throw new BusinessException("REFUND_ITEM_EXCEEDS_SOLD_QUANTITY",
						"La cantidad devuelta de " + saleItem.name() + " supera lo vendido disponible.");
			}
			if (requested.restock() && (!"PHYSICAL".equals(saleItem.itemType()) || saleItem.productId() == null)) {
				throw new BusinessException("REFUND_ITEM_NOT_RESTOCKABLE",
						"Solo los productos fisicos pueden regresar al inventario.");
			}
			validated.add(new ValidatedRefundItem(
					requested.saleItemId(), saleItem.productId(), saleItem.itemType(), requested.quantity(), requested.restock()
			));
		}
		return List.copyOf(validated);
	}

	private void validatePaymentSelection(GymOsPrincipal principal, UUID saleId, RequestRefundCommand request) {
		if (request.paymentId() == null) return;
		UUID paymentSaleId = lookupService.saleForPayment(principal, request.paymentId()).id();
		if (!paymentSaleId.equals(saleId)) throw new BusinessException("PAYMENT_SALE_MISMATCH", "El pago no pertenece a la venta.");
		var payment = lookupService.payment(principal, request.paymentId());
		if (!"COMPLETED".equals(payment.status())) throw new BusinessException("PAYMENT_NOT_COMPLETED", "Solo puedes reembolsar pagos completados.");
		if (request.amountCents() > payment.amountCents()) throw new BusinessException("REFUND_EXCEEDS_PAYMENT", "El reembolso no puede superar el pago seleccionado.");
	}

	private BusinessException mixedMode() {
		return new BusinessException("REFUND_MODE_MIXED",
				"Una venta no puede mezclar reembolsos monetarios generales con devoluciones por producto.");
	}
}

record RefundValidationResult(long alreadyRefunded, List<ValidatedRefundItem> items) {
	RefundValidationResult {
		items = items == null ? List.of() : List.copyOf(items);
	}
}

record ValidatedRefundItem(UUID saleItemId, UUID productId, String itemType, int quantity, boolean restock) {
}
