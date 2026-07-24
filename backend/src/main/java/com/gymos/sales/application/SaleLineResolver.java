package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.sales.application.command.SaleItemCommand;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.ProductForSale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SaleLineResolver {
	private final SalesRepository salesRepository;

	SaleLineResolver(SalesRepository salesRepository) {
		this.salesRepository = salesRepository;
	}

	ResolvedSale resolve(UUID organizationId, UUID branchId, UUID clientId,
			List<SaleItemCommand> items, long requestedHeaderDiscountCents) {
		if (items == null || items.isEmpty()) {
			throw new BusinessException("SALE_EMPTY", "La venta necesita al menos un item.");
		}

		Map<UUID, ProductForSale> products = new LinkedHashMap<>();
		List<ResolvedSaleLine> lines = new ArrayList<>(items.size());
		long subtotalCents = 0;
		long itemDiscountCents = 0;
		String currency = null;
		boolean containsMembershipPlan = false;

		for (SaleItemCommand item : items) {
			ProductForSale product = products.computeIfAbsent(
					item.productId(),
					productId -> salesRepository.productForSale(organizationId, branchId, productId)
							.orElseThrow(() -> new BusinessException(
									"PRODUCT_NOT_FOUND", "Un producto no existe, no esta activo o no pertenece a esta sucursal."))
			);
			if (currency == null) {
				currency = product.currency();
			} else if (!currency.equals(product.currency())) {
				throw new BusinessException("SALE_MIXED_CURRENCY", "Todos los items deben usar la misma moneda.");
			}

			long lineSubtotalCents = product.priceCents() * item.quantity();
			long discountCents = Math.max(0, item.discountCents());
			if (discountCents > lineSubtotalCents) {
				throw new BusinessException("SALE_ITEM_DISCOUNT_INVALID", "El descuento de un item no puede superar su subtotal.");
			}

			containsMembershipPlan |= "MEMBERSHIP_PLAN".equals(product.productType());
			subtotalCents += lineSubtotalCents;
			itemDiscountCents += discountCents;
			lines.add(new ResolvedSaleLine(
					product.id(),
					product.productType(),
					product.name(),
					item.quantity(),
					product.priceCents(),
					discountCents,
					lineSubtotalCents - discountCents
			));
		}

		long headerDiscountCents = Math.max(0, requestedHeaderDiscountCents);
		long totalDiscountCents = itemDiscountCents + headerDiscountCents;
		if (totalDiscountCents > subtotalCents) {
			throw new BusinessException("SALE_DISCOUNT_INVALID", "El descuento total no puede superar el subtotal.");
		}
		if (containsMembershipPlan && clientId == null) {
			throw new BusinessException("SALE_CLIENT_REQUIRED_FOR_PLAN", "Una venta de membresia necesita un cliente.");
		}

		return new ResolvedSale(
				List.copyOf(lines),
				subtotalCents,
				totalDiscountCents,
				subtotalCents - totalDiscountCents,
				currency,
				containsMembershipPlan
		);
	}

	record ResolvedSale(
			List<ResolvedSaleLine> lines,
			long subtotalCents,
			long discountCents,
			long totalCents,
			String currency,
			boolean containsMembershipPlan
	) {
	}

	record ResolvedSaleLine(
			UUID productId,
			String itemType,
			String name,
			int quantity,
			long unitPriceCents,
			long discountCents,
			long totalCents
	) {
	}
}
