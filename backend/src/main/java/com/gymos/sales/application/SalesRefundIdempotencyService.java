package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.sales.application.command.RefundItemCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.RefundItemSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
		List<RefundItemSnapshot> storedItems = salesRepository.refundItems(organizationId, refund.id());
		if (!refund.saleId().equals(saleId)
				|| refund.amountCents() != request.amountCents()
				|| !Objects.equals(request.paymentId(), refund.paymentId())
				|| !sameItems(request.items(), storedItems)) {
			throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada en otro reembolso.");
		}
		return new IdempotentRefund(idempotencyKey, Optional.of(refund.saleId()));
	}

	private boolean sameItems(List<RefundItemCommand> requested, List<RefundItemSnapshot> stored) {
		if (requested.size() != stored.size()) return false;
		List<RefundItemCommand> requestedSorted = requested.stream()
				.sorted(Comparator.comparing(RefundItemCommand::saleItemId))
				.toList();
		List<RefundItemSnapshot> storedSorted = stored.stream()
				.sorted(Comparator.comparing(RefundItemSnapshot::saleItemId))
				.toList();
		for (int index = 0; index < requestedSorted.size(); index++) {
			RefundItemCommand requestItem = requestedSorted.get(index);
			RefundItemSnapshot storedItem = storedSorted.get(index);
			if (!requestItem.saleItemId().equals(storedItem.saleItemId())
					|| requestItem.quantity() != storedItem.quantity()
					|| requestItem.restock() != storedItem.restock()) return false;
		}
		return true;
	}

	record IdempotentRefund(String idempotencyKey, Optional<UUID> existingSaleId) {
	}
}
