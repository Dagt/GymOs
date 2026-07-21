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
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.multipart.MultipartFile;

class SalesPaymentBranchIsolationTests {
	@Test
	void addingPaymentToSaleFromAnotherBranchIsDeniedBeforeMutation() {
		Fixture fixture = fixture();
		ManualPaymentCommand command = new ManualPaymentCommand(
				"CASH",
				1_000,
				null,
				null,
				true,
				null,
				"payment-test"
		);

		assertThatThrownBy(() -> fixture.service().addPayment(
				fixture.principal(),
				fixture.saleId(),
				command,
				"test-correlation"
		))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("SALE_BRANCH_MISMATCH"));

		verify(fixture.salesRepository()).saleHeader(fixture.organizationId(), fixture.saleId());
		verifyNoInteractions(fixture.jdbcClient(), fixture.auditService(), fixture.objectStorageService(), fixture.eventPublisher());
	}

	@Test
	void uploadingReceiptToPaymentFromAnotherBranchIsDeniedBeforeStorageOrMutation() {
		Fixture fixture = fixture();
		UUID paymentId = UUID.randomUUID();
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(fixture.salesRepository().saleIdForPayment(fixture.organizationId(), paymentId))
				.thenReturn(Optional.of(fixture.saleId()));

		assertThatThrownBy(() -> fixture.service().uploadReceipt(
				fixture.principal(),
				paymentId,
				file,
				"test-correlation"
		))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("SALE_BRANCH_MISMATCH"));

		verify(fixture.salesRepository()).saleIdForPayment(fixture.organizationId(), paymentId);
		verify(fixture.salesRepository()).saleHeader(fixture.organizationId(), fixture.saleId());
		verifyNoInteractions(fixture.jdbcClient(), fixture.auditService(), fixture.objectStorageService(), fixture.eventPublisher());
	}

	private Fixture fixture() {
		JdbcClient jdbcClient = mock(JdbcClient.class);
		SalesRepository salesRepository = mock(SalesRepository.class);
		AuditService auditService = mock(AuditService.class);
		ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		SalesService service = new SalesService(
				jdbcClient,
				salesRepository,
				auditService,
				objectStorageService,
				eventPublisher
		);

		UUID organizationId = UUID.randomUUID();
		UUID activeBranchId = UUID.randomUUID();
		UUID foreignBranchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(),
				organizationId,
				activeBranchId,
				"manager@gymos.local",
				"Branch Manager",
				Set.of("payment.create", "payment.receipt.upload")
		);
		OffsetDateTime now = OffsetDateTime.now();
		SaleHeader foreignSale = new SaleHeader(
				saleId,
				"S-TEST",
				foreignBranchId,
				UUID.randomUUID(),
				"Cliente",
				"PENDING_PAYMENT",
				10_000,
				0,
				10_000,
				0,
				10_000,
				"MXN",
				null,
				now,
				now,
				now
		);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(foreignSale));

		return new Fixture(
				service,
				jdbcClient,
				salesRepository,
				auditService,
				objectStorageService,
				eventPublisher,
				principal,
				organizationId,
				saleId
		);
	}

	private record Fixture(
			SalesService service,
			JdbcClient jdbcClient,
			SalesRepository salesRepository,
			AuditService auditService,
			ObjectStorageService objectStorageService,
			ApplicationEventPublisher eventPublisher,
			GymOsPrincipal principal,
			UUID organizationId,
			UUID saleId
	) {
	}
}
