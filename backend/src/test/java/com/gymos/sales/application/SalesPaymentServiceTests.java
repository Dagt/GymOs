package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.storage.ObjectStorageService;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.infrastructure.SalesPaymentCommandRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.SaleHeader;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SalesPaymentServiceTests {
	@Test
	void recordsPaymentAndPreservesFinancialRecalculationBoundary() {
		Fixture fixture = fixture();
		ManualPaymentCommand command = new ManualPaymentCommand("CASH", 1_000, null, null, true, null, "payment-key");
		when(fixture.salesRepository().paymentRequestHashByIdempotencyKey(fixture.organizationId(), "payment-key"))
				.thenReturn(Optional.empty());
		when(fixture.commandRepository().saleIdByIdempotencyKey(fixture.organizationId(), "payment-key"))
				.thenReturn(Optional.empty());

		fixture.service().record(fixture.principal(), fixture.saleId(), command, "correlation");

		verify(fixture.commandRepository()).create(any());
		verify(fixture.auditService()).record(eq(fixture.principal()), eq("PAYMENT_RECORDED"), eq("PAYMENT"), any(), eq("POS"), isNull(), eq("correlation"));
		verify(fixture.eventPublisher()).publishEvent(any(Object.class));
	}

	@Test
	void rejectsCompletedPaymentBeforeMutation() {
		Fixture fixture = fixture();
		UUID paymentId = UUID.randomUUID();
		when(fixture.salesRepository().saleIdForPayment(fixture.organizationId(), paymentId)).thenReturn(Optional.of(fixture.saleId()));
		when(fixture.salesRepository().payment(fixture.organizationId(), paymentId)).thenReturn(Optional.of(new PaymentDetail(
				paymentId, "TRANSFER", "COMPLETED", 1_000, "MXN", null, null,
				OffsetDateTime.now(), OffsetDateTime.now(), null
		)));

		assertThatThrownBy(() -> fixture.service().reject(fixture.principal(), paymentId, "duplicate", "correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("PAYMENT_ALREADY_COMPLETED"));

		verify(fixture.commandRepository(), never()).reject(any(), any(), any(), any(), any());
	}

	private Fixture fixture() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesPaymentCommandRepository commandRepository = mock(SalesPaymentCommandRepository.class);
		SalePaymentCompletionService completionService = mock(SalePaymentCompletionService.class);
		AuditService auditService = mock(AuditService.class);
		ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesPaymentService service = new SalesPaymentService(
				new SalesPaymentRecordingService(salesRepository, lookupService, commandRepository, auditService, eventPublisher),
				new SalesPaymentReceiptService(lookupService, commandRepository, auditService, objectStorageService),
				new SalesPaymentLifecycleService(lookupService, commandRepository, completionService, auditService, eventPublisher)
		);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(UUID.randomUUID(), organizationId, branchId, "owner", "Owner", Set.of());
		OffsetDateTime now = OffsetDateTime.now();
		SaleHeader sale = new SaleHeader(
				saleId, "S-TEST", branchId, UUID.randomUUID(), "Cliente", "PENDING_PAYMENT", "RETAIL_SALE",
				1_000, 0, 1_000, 0, 1_000, "MXN", null, now, now, now
		);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale));
		return new Fixture(service, salesRepository, commandRepository, completionService, auditService, eventPublisher, principal, organizationId, saleId);
	}

	private record Fixture(
			SalesPaymentService service,
			SalesRepository salesRepository,
			SalesPaymentCommandRepository commandRepository,
			SalePaymentCompletionService completionService,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher,
			GymOsPrincipal principal,
			UUID organizationId,
			UUID saleId
	) {
	}
}
