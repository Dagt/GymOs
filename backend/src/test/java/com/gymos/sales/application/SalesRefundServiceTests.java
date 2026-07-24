package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.events.RefundProcessedV1;
import com.gymos.sales.api.events.SaleItemsRefundedV1;
import com.gymos.sales.application.command.RefundItemCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRefundCommandRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.RefundDetail;
import com.gymos.sales.model.RefundableSaleItem;
import com.gymos.sales.model.SaleHeader;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class SalesRefundServiceTests {
	@Test
	void processesRefundWithinAvailableBalance() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesRefundCommandRepository commandRepository = mock(SalesRefundCommandRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		AuditService auditService = mock(AuditService.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesRefundService service = service(salesRepository, commandRepository, eventPublisher, auditService, lookupService);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID refundId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId, branchId);
		SaleHeader sale = sale(saleId, branchId, 5_000);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale));
		when(salesRepository.processedRefundTotal(organizationId, saleId)).thenReturn(1_000L);
		when(commandRepository.createProcessed(
				organizationId, branchId, saleId, null, 2_000, "MXN", "Solicitud", "refund-key", principal.userId()
		)).thenReturn(refundId);

		UUID result = service.process(principal, saleId, new RequestRefundCommand(null, 2_000, "Solicitud", "refund-key"), "correlation");

		assertThat(result).isEqualTo(saleId);
		verify(commandRepository).createProcessed(organizationId, branchId, saleId, null, 2_000, "MXN", "Solicitud", "refund-key", principal.userId());
		verify(commandRepository, never()).createItems(any(), any(), any(), any(), anyList(), any());
		verify(eventPublisher, never()).publishEvent(any());
		verify(auditService).record(principal, "SALE_REFUND_PROCESSED", "SALE_REFUND", refundId, "POS", "Solicitud", "correlation");
	}

	@Test
	void fullRefundPublishesInventoryRestorationEvent() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesRefundCommandRepository commandRepository = mock(SalesRefundCommandRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		AuditService auditService = mock(AuditService.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesRefundService service = service(salesRepository, commandRepository, eventPublisher, auditService, lookupService);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID refundId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId, branchId);
		SaleHeader sale = sale(saleId, branchId, 5_000);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale));
		when(salesRepository.processedRefundTotal(organizationId, saleId)).thenReturn(0L);
		when(commandRepository.createProcessed(
				organizationId, branchId, saleId, null, 5_000, "MXN", "Reembolso total", "refund-key", principal.userId()
		)).thenReturn(refundId);

		service.process(principal, saleId,
				new RequestRefundCommand(null, 5_000, "Reembolso total", "refund-key"), correlationId.toString());

		assertRestorationEvent(eventPublisher, organizationId, branchId, saleId, refundId, principal.userId(), correlationId);
	}

	@Test
	void accumulatedPartialRefundsPublishRestorationWhenPaidBalanceReachesZero() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesRefundCommandRepository commandRepository = mock(SalesRefundCommandRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		AuditService auditService = mock(AuditService.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesRefundService service = service(salesRepository, commandRepository, eventPublisher, auditService, lookupService);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID refundId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId, branchId);
		SaleHeader sale = sale(saleId, branchId, 5_000);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale));
		when(salesRepository.processedRefundTotal(organizationId, saleId)).thenReturn(3_000L);
		when(commandRepository.createProcessed(
				organizationId, branchId, saleId, null, 2_000, "MXN", "Cierre acumulado", "refund-key", principal.userId()
		)).thenReturn(refundId);

		service.process(principal, saleId,
				new RequestRefundCommand(null, 2_000, "Cierre acumulado", "refund-key"), correlationId.toString());

		assertRestorationEvent(eventPublisher, organizationId, branchId, saleId, refundId, principal.userId(), correlationId);
	}

	@Test
	void itemizedRefundPersistsLinesAndPublishesSelectiveEvent() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesRefundCommandRepository commandRepository = mock(SalesRefundCommandRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		AuditService auditService = mock(AuditService.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesRefundService service = service(salesRepository, commandRepository, eventPublisher, auditService, lookupService);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID saleItemId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID refundId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId, branchId);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale(saleId, branchId, 5_000)));
		when(salesRepository.processedRefundTotal(organizationId, saleId)).thenReturn(0L);
		when(salesRepository.refundableSaleItems(organizationId, saleId)).thenReturn(List.of(
				new RefundableSaleItem(saleItemId, productId, "PHYSICAL", "Agua", 3, 1)
		));
		when(commandRepository.createProcessed(
				organizationId, branchId, saleId, null, 1_000, "MXN", "Devolucion", "refund-key", principal.userId()
		)).thenReturn(refundId);
		List<RefundItemCommand> items = List.of(new RefundItemCommand(saleItemId, 2, true));

		service.process(principal, saleId,
				new RequestRefundCommand(null, 1_000, "Devolucion", "refund-key", items), correlationId.toString());

		verify(commandRepository).createItems(organizationId, branchId, saleId, refundId, items, principal.userId());
		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue()).isInstanceOfSatisfying(SaleItemsRefundedV1.class, event -> {
			assertThat(event.refundId()).isEqualTo(refundId);
			assertThat(event.items()).singleElement().satisfies(item -> {
				assertThat(item.saleItemId()).isEqualTo(saleItemId);
				assertThat(item.productId()).isEqualTo(productId);
				assertThat(item.quantity()).isEqualTo(2);
				assertThat(item.restock()).isTrue();
			});
		});
	}

	@Test
	void itemizedRefundRejectsQuantityAlreadyReturned() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesRefundCommandRepository commandRepository = mock(SalesRefundCommandRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		AuditService auditService = mock(AuditService.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesRefundService service = service(salesRepository, commandRepository, eventPublisher, auditService, lookupService);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID saleItemId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId, branchId);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale(saleId, branchId, 5_000)));
		when(salesRepository.refundableSaleItems(organizationId, saleId)).thenReturn(List.of(
				new RefundableSaleItem(saleItemId, UUID.randomUUID(), "PHYSICAL", "Agua", 2, 1)
		));

		assertThatThrownBy(() -> service.process(principal, saleId,
				new RequestRefundCommand(null, 1_000, "Devolucion", "refund-key",
						List.of(new RefundItemCommand(saleItemId, 2, true))), "correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("REFUND_ITEM_EXCEEDS_SOLD_QUANTITY"));
		verify(commandRepository, never()).createProcessed(any(), any(), any(), any(),
				org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), any());
	}

	@Test
	void itemizedRefundCannotMixWithAmountOnlyRefunds() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesRefundCommandRepository commandRepository = mock(SalesRefundCommandRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		AuditService auditService = mock(AuditService.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesRefundService service = service(salesRepository, commandRepository, eventPublisher, auditService, lookupService);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId, branchId);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale(saleId, branchId, 5_000)));
		when(salesRepository.hasAmountOnlyRefunds(organizationId, saleId)).thenReturn(true);

		assertThatThrownBy(() -> service.process(principal, saleId,
				new RequestRefundCommand(null, 1_000, "Devolucion", "refund-key",
						List.of(new RefundItemCommand(UUID.randomUUID(), 1, true))), "correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("REFUND_MODE_MIXED"));
	}

	@Test
	void reusedKeyWithDifferentAmountIsRejectedBeforeMutation() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesRefundCommandRepository commandRepository = mock(SalesRefundCommandRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		AuditService auditService = mock(AuditService.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesRefundService service = service(salesRepository, commandRepository, eventPublisher, auditService, lookupService);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId, branchId);
		OffsetDateTime now = OffsetDateTime.now();
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale(saleId, branchId, 5_000)));
		when(salesRepository.refundByIdempotencyKey(organizationId, "refund-key")).thenReturn(Optional.of(new RefundDetail(
				UUID.randomUUID(), saleId, null, "PROCESSED", 1_000, "MXN", "Anterior", "refund-key",
				principal.userId(), now, now, now, now
		)));

		assertThatThrownBy(() -> service.process(
				principal, saleId, new RequestRefundCommand(null, 2_000, "Nueva", "refund-key"), "correlation"
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));

		verify(commandRepository, never()).createProcessed(
				any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(), any(), any(), any()
		);
		verify(eventPublisher, never()).publishEvent(any());
	}

	private SalesRefundService service(SalesRepository salesRepository, SalesRefundCommandRepository commandRepository,
			ApplicationEventPublisher eventPublisher, AuditService auditService, SalesLookupService lookupService) {
		return new SalesRefundService(
				lookupService,
				new SalesRefundIdempotencyService(salesRepository),
				new SalesRefundValidationService(salesRepository, lookupService),
				new SalesRefundProcessor(commandRepository, eventPublisher, auditService)
		);
	}

	private void assertRestorationEvent(ApplicationEventPublisher eventPublisher, UUID organizationId, UUID branchId,
			UUID saleId, UUID refundId, UUID actorUserId, UUID correlationId) {
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue()).isInstanceOfSatisfying(RefundProcessedV1.class, event -> {
			assertThat(event.organizationId()).isEqualTo(organizationId);
			assertThat(event.branchId()).isEqualTo(branchId);
			assertThat(event.saleId()).isEqualTo(saleId);
			assertThat(event.refundId()).isEqualTo(refundId);
			assertThat(event.actorUserId()).isEqualTo(actorUserId);
			assertThat(event.occurredAt()).isNotNull();
			assertThat(event.correlationId()).isEqualTo(correlationId);
		});
	}

	private GymOsPrincipal principal(UUID organizationId, UUID branchId) {
		return new GymOsPrincipal(UUID.randomUUID(), organizationId, branchId, "owner", "Owner", Set.of("sale.refund"));
	}

	private SaleHeader sale(UUID saleId, UUID branchId, long paidCents) {
		OffsetDateTime now = OffsetDateTime.now();
		return new SaleHeader(saleId, "S-TEST", branchId, UUID.randomUUID(), "Cliente", "PAID",
				5_000, 0, 5_000, paidCents, Math.max(0, 5_000 - paidCents), "MXN", null, now, now, now);
	}
}
