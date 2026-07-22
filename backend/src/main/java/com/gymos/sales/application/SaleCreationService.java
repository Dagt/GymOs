package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.events.SaleCreatedV1;
import com.gymos.sales.application.command.CreateSaleCommand;
import com.gymos.sales.infrastructure.SaleCommandRepository;
import com.gymos.sales.infrastructure.SaleCommandRepository.SaleItemRecord;
import com.gymos.sales.infrastructure.SaleCommandRepository.SaleRecord;
import com.gymos.sales.infrastructure.SalesRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class SaleCreationService {
	private static final DateTimeFormatter SALE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final SalesRepository salesRepository;
	private final SaleLineResolver saleLineResolver;
	private final SaleCommandRepository commandRepository;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	SaleCreationService(
			SalesRepository salesRepository,
			SaleLineResolver saleLineResolver,
			SaleCommandRepository commandRepository,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher
	) {
		this.salesRepository = salesRepository;
		this.saleLineResolver = saleLineResolver;
		this.commandRepository = commandRepository;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	CreationResult create(GymOsPrincipal principal, CreateSaleCommand request, String correlationId) {
		String requestHash = requestHash(request);
		String idempotencyKey = blankToNull(request.idempotencyKey());
		if (idempotencyKey != null) {
			var existing = salesRepository.saleHeaderByIdempotencyKey(principal.organizationId(), idempotencyKey);
			if (existing.isPresent()) {
				ensureSameRequest(
						salesRepository.saleRequestHashByIdempotencyKey(principal.organizationId(), idempotencyKey).orElse(null),
						requestHash
				);
				return new CreationResult(existing.get().id(), false);
			}
		}
		if (request.clientId() != null && !salesRepository.clientExists(principal.organizationId(), request.clientId())) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
		}

		var resolved = saleLineResolver.resolve(
				principal.organizationId(),
				request.clientId(),
				request.items(),
				request.discountCents()
		);
		OffsetDateTime now = OffsetDateTime.now();
		UUID saleId = UUID.randomUUID();
		String saleNumber = "S-" + now.format(SALE_NUMBER_FORMAT) + "-" + saleId.toString().substring(0, 6).toUpperCase();

		commandRepository.createSale(new SaleRecord(
				saleId,
				principal.organizationId(),
				principal.branchId(),
				request.clientId(),
				saleNumber,
				resolved.subtotalCents(),
				resolved.discountCents(),
				resolved.totalCents(),
				resolved.currency(),
				blankToNull(request.notes()),
				now,
				principal.userId(),
				idempotencyKey,
				requestHash
		));
		commandRepository.createItems(
				principal.organizationId(),
				saleId,
				resolved.lines().stream()
						.map(line -> new SaleItemRecord(
								line.productId(),
								line.itemType(),
								line.name(),
								line.quantity(),
								line.unitPriceCents(),
								line.discountCents(),
								line.totalCents()
						))
						.toList(),
				now
		);

		auditService.record(principal, "SALE_CREATED", "SALE", saleId, "POS", null, correlationId);
		eventPublisher.publishEvent(new SaleCreatedV1(
				"sale-created:" + saleId,
				principal.organizationId(),
				principal.branchId(),
				saleId,
				saleNumber,
				null,
				resolved.totalCents(),
				resolved.currency(),
				now
		));
		return new CreationResult(saleId, true);
	}

	private String requestHash(CreateSaleCommand request) {
		StringBuilder value = new StringBuilder()
				.append("client=").append(request.clientId()).append('|')
				.append("discount=").append(request.discountCents()).append('|')
				.append("notes=").append(normalized(request.notes())).append('|');
		request.items().stream()
				.sorted(java.util.Comparator.comparing(item -> item.productId().toString()))
				.forEach(item -> value
						.append("item=").append(item.productId())
						.append(',').append(item.quantity())
						.append(',').append(item.discountCents())
						.append('|'));
		if (request.payment() != null) {
			value.append("payment=").append(paymentRequestHash(
					request.payment().method(),
					request.payment().amountCents(),
					request.payment().reference(),
					request.payment().receiptObjectKey(),
					request.payment().validateNow(),
					request.payment().notes()
			));
		}
		return sha256(value.toString());
	}

	private String paymentRequestHash(
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

	private void ensureSameRequest(String existingHash, String requestedHash) {
		if (existingHash != null && !existingHash.equals(requestedHash)) {
			throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada con una solicitud diferente.");
		}
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required", exception);
		}
	}

	private String normalized(String value) {
		return value == null ? "" : value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	record CreationResult(UUID saleId, boolean created) {
	}
}
