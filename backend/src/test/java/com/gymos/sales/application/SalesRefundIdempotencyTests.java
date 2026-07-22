package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.storage.ObjectStorageService;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.infrastructure.SalesRepository.RefundDetail;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;

class SalesRefundIdempotencyTests {
	@Test
	void reusedRefundKeyWithDifferentAmountIsRejectedBeforeMutation() {
		JdbcClient jdbcClient = mock(JdbcClient.class);
		SalesRepository salesRepository = mock(SalesRepository.class);
		AuditService auditService = mock(AuditService.class);
		ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		SalesService salesService = new SalesService(
				jdbcClient,
				salesRepository,
				new SaleLineResolver(salesRepository),
				auditService,
				objectStorageService,
				eventPublisher
		);

		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID refundId = UUID.randomUUID();
		String idempotencyKey = "refund-operation-1";
		OffsetDateTime now = OffsetDateTime.now();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				branchId,
				"manager@gymos.local",
				"Branch Manager",
				Set.of("sale.refund")
		);
		SaleHeader sale = new SaleHeader(
				saleId,
				"S-TEST",
				branchId,
				UUID.randomUUID(),
				"Cliente",
				"PAID",
				10_000,
				0,
				10_000,
				10_000,
				0,
				"MXN",
				null,
				now,
				now,
				now
		);
		RefundDetail existingRefund = new RefundDetail(
				refundId,
				saleId,
				null,
				"PROCESSED",
				1_000,
				"MXN",
				"Primer reembolso",
				idempotencyKey,
				userId,
				now,
				now,
				now,
				now
		);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale));
		when(salesRepository.refundByIdempotencyKey(organizationId, idempotencyKey))
				.thenReturn(Optional.of(existingRefund));

		RequestRefundCommand command = new RequestRefundCommand(
				null,
				2_000,
				"Reintento con importe diferente",
				idempotencyKey
		);

		assertThatThrownBy(() -> salesService.requestRefund(principal, saleId, command, "test-correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));

		verify(salesRepository).saleHeader(organizationId, saleId);
		verify(salesRepository).refundByIdempotencyKey(organizationId, idempotencyKey);
		verifyNoInteractions(jdbcClient, auditService, objectStorageService, eventPublisher);
	}
}
