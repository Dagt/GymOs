package com.gymos.inventory.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gymos.catalog.api.events.ProductVariantInventoryDefaultsRequestedV1;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.inventory.application.InventoryService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryCatalogEventListenerTests {
	@Test
	void appliesTheCommonReorderPointToEveryIndependentVariant() {
		InventoryService inventory = mock(InventoryService.class);
		InventoryCatalogEventListener listener = new InventoryCatalogEventListener(inventory);
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"owner", "Owner", Set.of("inventory.manage"));
		UUID appleId = UUID.randomUUID();
		UUID gumId = UUID.randomUUID();

		listener.applyVariantDefaults(new ProductVariantInventoryDefaultsRequestedV1(
				principal, List.of(appleId, gumId), 5, "test"));

		verify(inventory).updateReorderPoint(principal, appleId, 5, "test");
		verify(inventory).updateReorderPoint(principal, gumId, 5, "test");
	}
}
