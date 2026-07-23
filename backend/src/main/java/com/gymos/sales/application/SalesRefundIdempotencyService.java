package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SalesRefundIdempotencyService {
	private final SalesRepository salesRepository;

	SalesRefundIdempotencyService(SalesRepository salesRepository) {
		this.salesRepository = salesRepository;
	}

	IdempotentRefund resolve(UUID organizationId, UUID saleId, RequestRefundCommand request) {
		String idempotencyKey = SalesIdempotency.blankToNull(request.idempotencyKey());
		if (idempotencyKey == null) {
			return new IdempotentRefund(null, Optional.empty());
		}
		var existing = salesRepository.refundByIdempotencyKey(organizationId, idempotencyKey);
		if (existing.isEmpty()) {
			return new IdempotentRefund(idempotencyKey, Optional.empty());
		}
		var refund = existing.get();
		if (!refund.saleId().equals(saleId)
				|| refund.amountCents() != request.amountCents()
				|| (request.paymentId() != null && !request.paymentId().equals(refund.paymentId()))) {
			throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada en otro reembolso.");
		}
		return new IdempotentRefund(idempotencyKey, Optional.of(refund.saleId()));
	}

	record IdempotentRefund(String idempotencyKey, Optional<UUID> existingSaleId) {
	}
}
