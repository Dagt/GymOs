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
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;

class SalesBranchIsolationTests {
	@Test
	void refundForSaleFromAnotherBranchIsDeniedBeforeAnyMutation() {
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
		UUID activeBranchId = UUID.randomUUID();
		UUID foreignBranchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				activeBranchId,
				"manager@gymos.local",
				"Branch Manager",
				Set.of("sale.refund")
		);
		OffsetDateTime now = OffsetDateTime.now();
		SaleHeader foreignSale = new SaleHeader(
				saleId,
				"S-TEST",
				foreignBranchId,
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
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(foreignSale));

		RequestRefundCommand command = new RequestRefundCommand(null, 1_000, "Solicitud de prueba", "refund-test");

		assertThatThrownBy(() -> salesService.requestRefund(principal, saleId, command, "test-correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("SALE_BRANCH_MISMATCH"));

		verify(salesRepository).saleHeader(organizationId, saleId);
		verifyNoInteractions(jdbcClient, auditService, objectStorageService, eventPublisher);
	}
}
