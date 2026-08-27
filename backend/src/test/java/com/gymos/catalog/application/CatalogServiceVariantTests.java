package com.gymos.catalog.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.catalog.api.events.ProductVariantInventoryDefaultsRequestedV1;
import com.gymos.catalog.application.command.CreateProductVariantsCommand;
import com.gymos.catalog.application.query.ProductFamilyView;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CatalogServiceVariantTests {
	private CatalogProductService products;
	private ApplicationEventPublisher events;
	private CatalogService service;

	@BeforeEach
	void setUp() {
		products = mock(CatalogProductService.class);
		events = mock(ApplicationEventPublisher.class);
		service = new CatalogService(
				products,
				mock(CatalogMembershipPlanService.class),
				mock(CatalogCategoryService.class),
				mock(CatalogProductImageService.class),
				mock(CatalogInsightsService.class),
				events
		);
	}

	@Test
	void commonReorderPointIsAppliedToEveryCreatedVariantInsideTheBatchWorkflow() {
		GymOsPrincipal principal = principal(Set.of("catalog.create", "inventory.manage"));
		CreateProductVariantsCommand request = request(5);
		ProductSummaryView apple = mock(ProductSummaryView.class);
		ProductSummaryView gum = mock(ProductSummaryView.class);
		UUID appleId = UUID.randomUUID();
		UUID gumId = UUID.randomUUID();
		when(apple.id()).thenReturn(appleId);
		when(gum.id()).thenReturn(gumId);
		when(products.createVariants(principal, request, "test"))
				.thenReturn(new ProductFamilyView(UUID.randomUUID(), "Psychotic", "Insane Labz", List.of(apple, gum)));

		service.createProductVariants(principal, request, "test");

		ArgumentCaptor<ProductVariantInventoryDefaultsRequestedV1> event =
				ArgumentCaptor.forClass(ProductVariantInventoryDefaultsRequestedV1.class);
		verify(events).publishEvent(event.capture());
		org.assertj.core.api.Assertions.assertThat(event.getValue().principal()).isEqualTo(principal);
		org.assertj.core.api.Assertions.assertThat(event.getValue().productIds())
				.containsExactly(appleId, gumId);
		org.assertj.core.api.Assertions.assertThat(event.getValue().reorderPoint()).isEqualTo(5);
		org.assertj.core.api.Assertions.assertThat(event.getValue().correlationId()).isEqualTo("test");
	}

	@Test
	void commonReorderPointRequiresInventoryPermissionBeforeCreatingAnything() {
		GymOsPrincipal principal = principal(Set.of("catalog.create"));
		CreateProductVariantsCommand request = request(5);

		assertThatThrownBy(() -> service.createProductVariants(principal, request, "test"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.code())
								.isEqualTo("INVENTORY_PERMISSION_REQUIRED"));
		verify(products, never()).createVariants(any(), any(), any());
		verify(events, never()).publishEvent(any());
	}

	private CreateProductVariantsCommand request(Integer reorderPoint) {
		return new CreateProductVariantsCommand(
				null, "Psychotic", "Insane Labz", null, "Bote", 55_000, 32_000L,
				"MXN", true, "ACTIVE", "PIECE", true, reorderPoint, 0,
				null, null, List.of(), List.of());
	}

	private GymOsPrincipal principal(Set<String> permissions) {
		return new GymOsPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"owner", "Owner", permissions);
	}
}
