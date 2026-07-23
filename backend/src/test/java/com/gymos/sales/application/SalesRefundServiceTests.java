package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRefundCommandRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.RefundDetail;
import com.gymos.sales.model.SaleHeader;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SalesRefundServiceTests {
	@Test
	void processesRefundWithinAvailableBalance() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesRefundCommandRepository commandRepository = mock(SalesRefundCommandRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		AuditService auditService = mock(AuditService.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesRefundService service = new SalesRefundService(
				lookupService,
				new SalesRefundIdempotencyService(salesRepository),
				new SalesRefundValidationService(salesRepository, lookupService),
				new SalesRefundProcessor(commandRepository, eventPublisher, auditService)
		);
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
		verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
		verify(auditService).record(principal, "SALE_REFUND_PROCESSED", "SALE_REFUND", refundId, "POS", "Solicitud", "correlation");
	}

	@Test
	void reusedKeyWithDifferentAmountIsRejectedBeforeMutation() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesRefundCommandRepository commandRepository = mock(SalesRefundCommandRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		AuditService auditService = mock(AuditService.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesRefundService service = new SalesRefundService(
				lookupService,
				new SalesRefundIdempotencyService(salesRepository),
				new SalesRefundValidationService(salesRepository, lookupService),
				new SalesRefundProcessor(commandRepository, eventPublisher, auditService)
		);
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
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
		);
		verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
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
