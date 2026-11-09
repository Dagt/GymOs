package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesFinancialIntegrityRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.SaleHeader;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SalesFinancialIntegrityGuardTests {
	@Test
	void addingPaymentAboveOutstandingBalanceIsRejected() {
		Fixture fixture = fixture(5_000, 4_000);
		ManualPaymentCommand request = new ManualPaymentCommand("CASH", 5_001, null, null, true, null, null);

		assertThatThrownBy(() -> fixture.guard().guardAddedPayment(fixture.principal(), fixture.saleId(), request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PAYMENT_EXCEEDS_BALANCE"));
	}

	@Test
	void validatingPendingPaymentAboveOutstandingBalanceIsRejected() {
		Fixture fixture = fixture(5_000, 4_000);
		when(fixture.salesRepository().payment(fixture.organizationId(), fixture.paymentId()))
				.thenReturn(Optional.of(payment(fixture.paymentId(), "PENDING_VALIDATION", 5_000)));
		assertThatThrownBy(() -> fixture.guard().guardValidatedPayment(fixture.principal(), fixture.paymentId()))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PAYMENT_EXCEEDS_BALANCE"));
	}

	@Test
	void accumulatedRefundsAboveSelectedPaymentAreRejected() {
		Fixture fixture = fixture(10_000, 0);
		when(fixture.salesRepository().payment(fixture.organizationId(), fixture.paymentId()))
				.thenReturn(Optional.of(payment(fixture.paymentId(), "COMPLETED", 10_000)));
		when(fixture.integrityRepository().processedRefundTotalForPayment(fixture.organizationId(), fixture.paymentId()))
				.thenReturn(7_000L);
		RequestRefundCommand request = new RequestRefundCommand(fixture.paymentId(), 3_001, "ajuste", null);

		assertThatThrownBy(() -> fixture.guard().guardRefund(fixture.principal(), fixture.saleId(), request))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("REFUND_EXCEEDS_PAYMENT"));
	}

	@Test
	void providerManagedPaymentCannotBeValidatedOrRejectedManually() {
		Fixture fixture = fixture(5_000, 5_000);
		when(fixture.salesRepository().payment(fixture.organizationId(), fixture.paymentId()))
				.thenReturn(Optional.of(payment(fixture.paymentId(), "CARD_MERCADO_PAGO", "PENDING_VALIDATION", 5_000)));

		assertThatThrownBy(() -> fixture.guard().guardValidatedPayment(fixture.principal(), fixture.paymentId()))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PROVIDER_PAYMENT_MANUAL_VALIDATION_FORBIDDEN"));
		when(fixture.integrityRepository().providerSubmissionMayHaveStarted(fixture.organizationId(), fixture.paymentId())).thenReturn(true);
		assertThatThrownBy(() -> fixture.guard().guardProviderPaymentRejection(fixture.principal(), fixture.paymentId()))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("PROVIDER_PAYMENT_MANUAL_REJECTION_FORBIDDEN"));
	}

	private Fixture fixture(long totalCents, long balanceCents) {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalesFinancialIntegrityRepository integrityRepository = mock(SalesFinancialIntegrityRepository.class);
		SalesFinancialIntegrityGuard guard = new SalesFinancialIntegrityGuard(
				salesRepository,
				integrityRepository,
				mock(com.gymos.payments.api.PaymentConfigurationOperations.class)
		);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(), organizationId, branchId, "manager@gymos.local", "Manager", Set.of()
		);
		OffsetDateTime now = OffsetDateTime.now();
		SaleHeader sale = new SaleHeader(
				saleId, "S-TEST", branchId, UUID.randomUUID(), "Cliente", "PENDING_PAYMENT", "RETAIL_SALE",
				totalCents, 0, totalCents, totalCents - balanceCents, balanceCents,
				"MXN", null, now, now, now
		);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale));
		when(salesRepository.saleIdForPayment(organizationId, paymentId)).thenReturn(Optional.of(saleId));
		return new Fixture(guard, salesRepository, integrityRepository, principal, organizationId, saleId, paymentId);
	}

	private PaymentDetail payment(UUID paymentId, String status, long amountCents) {
		return payment(paymentId, "TRANSFER", status, amountCents);
	}

	private PaymentDetail payment(UUID paymentId, String method, String status, long amountCents) {
		OffsetDateTime now = OffsetDateTime.now();
		return new PaymentDetail(paymentId, method, status, amountCents, "MXN", null, null, now, null, null);
	}

	private record Fixture(
			SalesFinancialIntegrityGuard guard,
			SalesRepository salesRepository,
			SalesFinancialIntegrityRepository integrityRepository,
			GymOsPrincipal principal,
			UUID organizationId,
			UUID saleId,
			UUID paymentId
	) {
	}
}
