package com.gymos.sales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.common.api.BusinessException;
import com.gymos.sales.application.SaleLineResolver.ResolvedSale;
import com.gymos.sales.application.command.SaleItemCommand;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.ProductForSale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SaleLineResolverTests {
	@Test
	void resolvesRepeatedProductOnlyOnceAndPreservesLines() {
		SalesRepository repository = mock(SalesRepository.class);
		SaleLineResolver resolver = new SaleLineResolver(repository);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		when(repository.productForSale(organizationId, branchId, productId)).thenReturn(Optional.of(
				new ProductForSale(productId, "Agua", "PHYSICAL", 50000, "MXN")
		));

		ResolvedSale result = resolver.resolve(
				organizationId,
				branchId,
				UUID.randomUUID(),
				List.of(
						new SaleItemCommand(productId, 1, 0),
						new SaleItemCommand(productId, 2, 5000)
				),
				10000
		);

		assertEquals(2, result.lines().size());
		assertEquals(150000, result.subtotalCents());
		assertEquals(15000, result.discountCents());
		assertEquals(135000, result.totalCents());
		assertEquals("MXN", result.currency());
		verify(repository, times(1)).productForSale(organizationId, branchId, productId);
	}

	@Test
	void rejectsMixedCurrencies() {
		SalesRepository repository = mock(SalesRepository.class);
		SaleLineResolver resolver = new SaleLineResolver(repository);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID firstProductId = UUID.randomUUID();
		UUID secondProductId = UUID.randomUUID();
		when(repository.productForSale(organizationId, branchId, firstProductId)).thenReturn(Optional.of(
				new ProductForSale(firstProductId, "Producto MXN", "RETAIL", 1000, "MXN")
		));
		when(repository.productForSale(organizationId, branchId, secondProductId)).thenReturn(Optional.of(
				new ProductForSale(secondProductId, "Producto USD", "RETAIL", 1000, "USD")
		));

		BusinessException exception = assertThrows(BusinessException.class, () -> resolver.resolve(
				organizationId,
				branchId,
				null,
				List.of(
						new SaleItemCommand(firstProductId, 1, 0),
						new SaleItemCommand(secondProductId, 1, 0)
				),
				0
		));

		assertEquals("SALE_MIXED_CURRENCY", exception.code());
	}

	@Test
	void rejectsMembershipPlanEvenWhenTheSaleHasAClient() {
		SalesRepository repository = mock(SalesRepository.class);
		SaleLineResolver resolver = new SaleLineResolver(repository);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		when(repository.productForSale(organizationId, branchId, productId)).thenReturn(Optional.of(
				new ProductForSale(productId, "Mensualidad", "MEMBERSHIP_PLAN", 50000, "MXN")
		));

		BusinessException exception = assertThrows(BusinessException.class, () -> resolver.resolve(
				organizationId,
				branchId,
				UUID.randomUUID(),
				List.of(new SaleItemCommand(productId, 1, 0)),
				0
		));

		assertEquals("SALE_MEMBERSHIP_OPERATION_REQUIRED", exception.code());
	}

	@Test
	void rejectsManagedVisitPackageFromGenericPosSoItCannotBeSoldWithoutBalance() {
		SalesRepository repository = mock(SalesRepository.class);
		SaleLineResolver resolver = new SaleLineResolver(repository);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		when(repository.productForSale(organizationId, branchId, productId)).thenReturn(Optional.of(
				new ProductForSale(productId, "10 visitas", "SERVICE", 90000, "MXN", true)
		));

		BusinessException exception = assertThrows(BusinessException.class, () -> resolver.resolve(
				organizationId, branchId, UUID.randomUUID(),
				List.of(new SaleItemCommand(productId, 1, 0)), 0));

		assertEquals("SALE_VISIT_PACKAGE_OPERATION_REQUIRED", exception.code());
	}
}
