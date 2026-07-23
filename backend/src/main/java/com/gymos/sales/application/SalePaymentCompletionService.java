package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.sales.api.events.PaymentCompletedV1;
import com.gymos.sales.infrastructure.SalePaymentCompletionRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class SalePaymentCompletionService {
	private final SalesRepository salesRepository;
	private final SalePaymentCompletionRepository repository;
	private final ApplicationEventPublisher eventPublisher;

	SalePaymentCompletionService(
			SalesRepository salesRepository,
			SalePaymentCompletionRepository repository,
			ApplicationEventPublisher eventPublisher
	) {
		this.salesRepository = salesRepository;
		this.repository = repository;
		this.eventPublisher = eventPublisher;
	}

	void recalculate(UUID organizationId, UUID saleId, UUID actorUserId) {
		var sale = salesRepository.saleHeader(organizationId, saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		long paidCents = repository.completedPaymentTotal(organizationId, saleId);
		long balanceCents = Math.max(0, sale.totalCents() - paidCents);
		String status = paidCents >= sale.totalCents()
				? "PAID"
				: paidCents > 0 ? "PARTIALLY_PAID" : "PENDING_PAYMENT";
		repository.updateFinancials(
				organizationId,
				saleId,
				status,
				paidCents,
				balanceCents,
				actorUserId,
				OffsetDateTime.now()
		);
		if ("PAID".equals(status)) {
			publishIfNeeded(organizationId, saleId);
		}
	}

	void publishIfNeeded(UUID organizationId, UUID saleId) {
		OffsetDateTime completedAt = OffsetDateTime.now();
		if (!repository.markPaymentCompleted(organizationId, saleId, completedAt)) {
			return;
		}
		var sale = salesRepository.saleHeader(organizationId, saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		var context = repository.context(organizationId, saleId);
		eventPublisher.publishEvent(new PaymentCompletedV1(
				"payment-completed:" + saleId,
				organizationId,
				sale.branchId(),
				saleId,
				sale.clientId(),
				sale.saleNumber(),
				context.businessContext(),
				context.contextMembershipId(),
				context.contextTargetPlanId(),
				repository.items(organizationId, saleId),
				completedAt
		));
	}
}
