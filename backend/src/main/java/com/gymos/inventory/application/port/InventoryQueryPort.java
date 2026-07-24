package com.gymos.inventory.application.port;

import com.gymos.inventory.application.query.InventoryMovementPageView;
import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import com.gymos.inventory.application.query.InventoryMovementView;
import com.gymos.inventory.application.query.InventoryProductView;
import com.gymos.inventory.application.query.InventoryStockPageView;
import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import com.gymos.inventory.application.query.InventoryStockView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryQueryPort {
	List<InventoryStockView> stock(UUID organizationId, UUID branchId);

	default InventoryStockPageView searchStock(UUID organizationId, UUID branchId, InventoryStockSearchCriteria criteria) {
		String query = criteria.query() == null ? "" : criteria.query().trim().toLowerCase();
		List<InventoryStockView> filtered = stock(organizationId, branchId).stream()
				.filter(item -> query.isBlank() || item.productName().toLowerCase().contains(query)
						|| item.sku().toLowerCase().contains(query))
				.toList();
		int from = Math.min(criteria.page() * criteria.size(), filtered.size());
		int to = Math.min(from + criteria.size(), filtered.size());
		return InventoryStockPageView.of(filtered.subList(from, to), criteria.page(), criteria.size(), filtered.size());
	}

	Optional<InventoryStockView> stockDetail(UUID organizationId, UUID branchId, UUID productId);
	List<InventoryStockView> lowStock(UUID organizationId, UUID branchId);
	List<InventoryMovementView> movements(UUID organizationId, UUID branchId, UUID productId, int limit);

	default InventoryMovementPageView searchMovements(UUID organizationId, UUID branchId,
			InventoryMovementSearchCriteria criteria) {
		List<InventoryMovementView> values = movements(
				organizationId, branchId, criteria.productId(), (criteria.page() + 1) * criteria.size());
		int from = Math.min(criteria.page() * criteria.size(), values.size());
		int to = Math.min(from + criteria.size(), values.size());
		return InventoryMovementPageView.of(values.subList(from, to), criteria.page(), criteria.size(), values.size());
	}

	Optional<InventoryProductView> product(UUID organizationId, UUID branchId, UUID productId);
}
