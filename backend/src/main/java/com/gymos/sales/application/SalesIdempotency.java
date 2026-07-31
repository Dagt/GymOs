package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.sales.application.command.CreateSaleCommand;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.api.operations.MembershipBenefitSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class SalesIdempotency {
	private SalesIdempotency() {
	}

	static String saleRequestHash(CreateSaleCommand request) {
		StringBuilder value = new StringBuilder()
				.append("client=").append(request.clientId()).append('|')
				.append("discount=").append(request.discountCents()).append('|')
				.append("notes=").append(normalized(request.notes())).append('|');
		request.items().stream()
				.sorted(Comparator.comparing(item -> item.productId().toString()))
				.forEach(item -> value
						.append("item=").append(item.productId())
						.append(',').append(item.quantity())
						.append(',').append(item.discountCents())
						.append('|'));
		if (request.payment() != null) {
			value.append("payment=").append(paymentRequestHash(request.payment()));
		}
		return sha256(value.toString());
	}

	static String paymentRequestHash(ManualPaymentCommand request) {
		return paymentRequestHash(
				request.method(), request.amountCents(), request.reference(), request.receiptObjectKey(),
				request.validateNow(), request.notes()
		);
	}

	static String paymentRequestHash(
			String method,
			long amountCents,
			String reference,
			String receiptObjectKey,
			boolean validateNow,
			String notes
	) {
		return sha256(String.join("|",
				"method=" + normalized(method).toUpperCase(),
				"amount=" + amountCents,
				"reference=" + normalized(reference),
				"receipt=" + normalized(receiptObjectKey),
				"validateNow=" + validateNow,
				"notes=" + normalized(notes)
		));
	}

	static String membershipSaleRequestHash(
			UUID clientId,
			UUID sourceMembershipId,
			UUID targetPlanId,
			UUID productId,
			long listPriceCents,
			long discountCents,
			long totalCents,
			String currency,
			List<MembershipBenefitSnapshot> appliedBenefits,
			String method,
			String reference,
			boolean validateNow,
			String notes,
			String businessContext
	) {
		StringBuilder value = new StringBuilder(String.join("|",
				"client=" + clientId,
				"sourceMembership=" + sourceMembershipId,
				"targetPlan=" + targetPlanId,
				"product=" + productId,
				"listPrice=" + listPriceCents,
				"discount=" + discountCents,
				"total=" + totalCents,
				"currency=" + normalized(currency),
				"method=" + normalized(method).toUpperCase(),
				"reference=" + normalized(reference),
				"validateNow=" + validateNow,
				"notes=" + normalized(notes),
				"context=" + businessContext
		));
		appliedBenefits.stream()
				.sorted(Comparator.comparing(snapshot -> snapshot.benefitId().toString()))
				.forEach(snapshot -> value
						.append("|benefit=").append(snapshot.benefitId())
						.append(',').append(normalized(snapshot.code()))
						.append(',').append(snapshot.discountCents()));
		return sha256(value.toString());
	}

	static void ensureSameRequest(String existingHash, String requestedHash) {
		if (existingHash != null && !existingHash.equals(requestedHash)) {
			throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada con una solicitud diferente.");
		}
	}

	static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String normalized(String value) {
		return value == null ? "" : value.trim();
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required", exception);
		}
	}
}
