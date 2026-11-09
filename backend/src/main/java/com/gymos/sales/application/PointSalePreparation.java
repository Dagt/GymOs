package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.MercadoPagoPointOperations.CreatePointOrderCommand;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.sales.api.events.PointSaleStockRequested;
import com.gymos.sales.infrastructure.SalePaymentCompletionRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.infrastructure.SalesFinancialIntegrityRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PointSalePreparation {
	private final SalesRepository sales;
	private final SalePaymentCompletionRepository completion;
	private final PaymentConfigurationOperations configuration;
	private final SalesFinancialIntegrityRepository integrity;
	private final ApplicationEventPublisher events;

	PointSalePreparation(SalesRepository sales, SalePaymentCompletionRepository completion,
			PaymentConfigurationOperations configuration, SalesFinancialIntegrityRepository integrity, ApplicationEventPublisher events) {
		this.sales = sales;
		this.completion = completion;
		this.configuration = configuration;
		this.integrity = integrity;
		this.events = events;
	}

	@Transactional
	public CreatePointOrderCommand prepare(GymOsPrincipal principal, UUID paymentId) {
		var sale = SalesRecordResolver.requirePaymentSaleForBranch(sales, principal, paymentId);
		UUID organizationId = principal.organizationId();
		sales.lockSaleForRefund(organizationId, sale.id());
		var payment = SalesRecordResolver.requirePayment(sales, organizationId, paymentId);
		if (!"CARD_MERCADO_PAGO".equals(payment.method())) {
			throw new BusinessException("MERCADO_PAGO_PAYMENT_METHOD_REQUIRED", "El pago no fue registrado como Mercado Pago Point.");
		}
		if ("COMPLETED".equals(payment.status())) {
			throw new BusinessException("PAYMENT_ALREADY_COMPLETED", "El pago ya fue confirmado.");
		}
		if (!"PENDING_VALIDATION".equals(payment.status())) {
			throw new BusinessException("PAYMENT_ALREADY_REJECTED", "El pago ya fue cerrado.");
		}
		configuration.requireReceptionMethod(organizationId, sale.branchId(), payment.method());
		events.publishEvent(new PointSaleStockRequested(organizationId, sale.branchId(), sale.id(), completion.items(organizationId, sale.id())));
		integrity.markProviderSubmissionPrepared(organizationId, paymentId);
		return new CreatePointOrderCommand(organizationId, sale.branchId(), sale.id(), paymentId, sale.clientId(),
				payment.amountCents(), payment.currency(), "mp-point:" + paymentId);
	}
}
