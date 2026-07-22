package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.CreateSaleCommand;
import com.gymos.sales.application.command.SaleItemCommand;
import com.gymos.sales.infrastructure.SaleCommandRepository;
import com.gymos.sales.infrastructure.SaleCommandRepository.SaleItemRecord;
import com.gymos.sales.infrastructure.SaleCommandRepository.SaleRecord;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.SaleHeader;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class SaleCreationServiceTests {
	@Test
	void persistsResolvedLinesAndPublishesOneCreationEvent() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SaleLineResolver lineResolver = mock(SaleLineResolver.class);
		SaleCommandRepository commandRepository = mock(SaleCommandRepository.class);
		AuditService auditService = mock(AuditService.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		SaleCreationService service = new SaleCreationService(
				salesRepository,
				lineResolver,
				commandRepository,
				auditService,
				eventPublisher
		);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				branchId,
				"owner",
				"Owner",
				Set.of("sale.create")
		);
		CreateSaleCommand command = new CreateSaleCommand(
				clientId,
				List.of(new SaleItemCommand(productId, 2, 100)),
				200,
				"Venta de prueba",
				null,
				"sale-key"
		);
		when(salesRepository.saleHeaderByIdempotencyKey(organizationId, "sale-key")).thenReturn(Optional.empty());
		when(salesRepository.clientExists(organizationId, clientId)).thenReturn(true);
		when(lineResolver.resolve(organizationId, clientId, command.items(), 200)).thenReturn(
				new SaleLineResolver.ResolvedSale(
						List.of(new SaleLineResolver.ResolvedSaleLine(productId, "PRODUCT", "Producto", 2, 1_000, 100, 1_900)),
						2_000,
						300,
						1_700,
						"MXN",
						false
				)
		);

		var result = service.create(principal, command, "correlation");

		assertThat(result.created()).isTrue();
		ArgumentCaptor<SaleRecord> saleCaptor = ArgumentCaptor.forClass(SaleRecord.class);
		verify(commandRepository).createSale(saleCaptor.capture());
		assertThat(saleCaptor.getValue().subtotalCents()).isEqualTo(2_000);
		assertThat(saleCaptor.getValue().discountCents()).isEqualTo(300);
		assertThat(saleCaptor.getValue().totalCents()).isEqualTo(1_700);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<SaleItemRecord>> itemsCaptor = ArgumentCaptor.forClass(List.class);
		verify(commandRepository).createItems(
				org.mockito.ArgumentMatchers.eq(organizationId),
				org.mockito.ArgumentMatchers.eq(result.saleId()),
				itemsCaptor.capture(),
				any(OffsetDateTime.class)
		);
		assertThat(itemsCaptor.getValue()).singleElement().satisfies(item -> {
			assertThat(item.productId()).isEqualTo(productId);
			assertThat(item.totalCents()).isEqualTo(1_900);
		});
		verify(auditService).record(principal, "SALE_CREATED", "SALE", result.saleId(), "POS", null, "correlation");
		verify(eventPublisher).publishEvent(any());
	}

	@Test
	void idempotentRetryReturnsExistingSaleWithoutWritingAgain() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SaleLineResolver lineResolver = mock(SaleLineResolver.class);
		SaleCommandRepository commandRepository = mock(SaleCommandRepository.class);
		AuditService auditService = mock(AuditService.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		SaleCreationService service = new SaleCreationService(salesRepository, lineResolver, commandRepository, auditService, eventPublisher);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID existingSaleId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(UUID.randomUUID(), organizationId, branchId, "owner", "Owner", Set.of());
		CreateSaleCommand command = new CreateSaleCommand(null, List.of(new SaleItemCommand(productId, 1, 0)), 0, null, null, "same-key");
		SaleHeader existing = new SaleHeader(
				existingSaleId,
				"S-EXISTING",
				branchId,
				null,
				null,
				"PENDING_PAYMENT",
				1_000,
				0,
				1_000,
				0,
				1_000,
				"MXN",
				null,
				OffsetDateTime.now(),
				OffsetDateTime.now(),
				OffsetDateTime.now()
		);
		when(salesRepository.saleHeaderByIdempotencyKey(organizationId, "same-key")).thenReturn(Optional.of(existing));
		when(salesRepository.saleRequestHashByIdempotencyKey(organizationId, "same-key")).thenReturn(Optional.empty());

		var result = service.create(principal, command, "correlation");

		assertThat(result.saleId()).isEqualTo(existingSaleId);
		assertThat(result.created()).isFalse();
		verify(commandRepository, never()).createSale(any());
		verify(commandRepository, never()).createItems(any(), any(), any(), any());
		verify(lineResolver, never()).resolve(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
	}
}
