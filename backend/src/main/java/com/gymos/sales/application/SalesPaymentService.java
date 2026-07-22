package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.storage.ObjectStorageService;
import com.gymos.sales.api.events.PaymentRecordedV1;
import com.gymos.sales.api.events.PaymentRejectedV1;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.infrastructure.SalesPaymentCommandRepository;
import com.gymos.sales.infrastructure.SalesPaymentCommandRepository.PaymentRecord;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.infrastructure.SalesRepository.PaymentDetail;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class SalesPaymentService {
	private final SalesRepository salesRepository;
	private final SalesPaymentCommandRepository commandRepository;
	private final SalePaymentCompletionService completionService;
	private final AuditService auditService;
	private final ObjectStorageService objectStorageService;
	private final ApplicationEventPublisher eventPublisher;

	SalesPaymentService(
			SalesRepository salesRepository,
			SalesPaymentCommandRepository commandRepository,
			SalePaymentCompletionService completionService,
			AuditService auditService,
			ObjectStorageService objectStorageService,
			ApplicationEventPublisher eventPublisher
	) {
		this.salesRepository = salesRepository;
		this.commandRepository = commandRepository;
		this.completionService = completionService;
		this.auditService = auditService;
		this.objectStorageService = objectStorageService;
		this.eventPublisher = eventPublisher;
	}

	void record(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request, String correlationId) {
		SaleHeader sale = ensureSale(principal, saleId);
		String requestHash = requestHash(request);
		String idempotencyKey = blankToNull(request.idempotencyKey());
		if (idempotencyKey != null) {
			var existingSaleId = commandRepository.saleIdByIdempotencyKey(principal.organizationId(), idempotencyKey);
			if (existingSaleId.isPresent()) {
				if (!existingSaleId.get().equals(saleId)) {
					throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada en otra venta.");
				}
				ensureSameRequest(
						salesRepository.paymentRequestHashByIdempotencyKey(principal.organizationId(), idempotencyKey).orElse(null),
						requestHash
				);
				return;
			}
		}
		if (request.amountCents() <= 0) {
			throw new BusinessException("PAYMENT_AMOUNT_INVALID", "El pago debe ser mayor a cero.");
		}

		String method = request.method().trim().toUpperCase();
		String status = request.validateNow() || "CASH".equals(method) ? "COMPLETED" : "PENDING_VALIDATION";
		OffsetDateTime now = OffsetDateTime.now();
		UUID paymentId = UUID.randomUUID();
		commandRepository.create(new PaymentRecord(
				paymentId,
				principal.organizationId(),
				principal.branchId(),
				saleId,
				sale.clientId(),
				method,
				status,
				request.amountCents(),
				sale.currency(),
				blankToNull(request.reference()),
				blankToNull(request.receiptObjectKey()),
				now,
				"COMPLETED".equals(status) ? now : null,
				"COMPLETED".equals(status) ? principal.userId() : null,
				blankToNull(request.notes()),
				principal.userId(),
				idempotencyKey,
				requestHash
		));
		auditService.record(principal, "PAYMENT_RECORDED", "PAYMENT", paymentId, "POS", null, correlationId);
		eventPublisher.publishEvent(new PaymentRecordedV1(
				"payment-recorded:" + paymentId,
				principal.organizationId(),
				principal.branchId(),
				saleId,
				paymentId,
				method,
				status,
				request.amountCents(),
				sale.currency(),
				now
		));
	}

	UUID uploadReceipt(GymOsPrincipal principal, UUID paymentId, MultipartFile file, String correlationId) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException("PAYMENT_RECEIPT_REQUIRED", "Selecciona un archivo de comprobante.");
		}
		UUID saleId = saleIdForPayment(principal, paymentId);
		SaleHeader sale = ensureSale(principal, saleId);
		String objectKey = receiptObjectKey(principal.organizationId(), saleId, paymentId, file.getOriginalFilename());
		try {
			objectStorageService.putReceipt(objectKey, file.getContentType(), file.getSize(), file.getInputStream());
		} catch (IOException exception) {
			throw new BusinessException("PAYMENT_RECEIPT_READ_FAILED", "No fue posible leer el comprobante.");
		}
		commandRepository.attachReceipt(principal.organizationId(), sale.id(), paymentId, objectKey, principal.userId(), OffsetDateTime.now());
		auditService.record(principal, "PAYMENT_RECEIPT_UPLOADED", "PAYMENT", paymentId, "POS", null, correlationId);
		return saleId;
	}

	UUID validate(GymOsPrincipal principal, UUID paymentId, String correlationId) {
		UUID saleId = saleIdForPayment(principal, paymentId);
		ensureSale(principal, saleId);
		PaymentDetail payment = payment(principal, paymentId);
		if ("COMPLETED".equals(payment.status())) {
			return saleId;
		}
		commandRepository.complete(principal.organizationId(), paymentId, principal.userId(), OffsetDateTime.now());
		auditService.record(principal, "PAYMENT_VALIDATED", "PAYMENT", paymentId, "POS", null, correlationId);
		completionService.recalculate(principal.organizationId(), saleId, principal.userId());
		return saleId;
	}

	UUID reject(GymOsPrincipal principal, UUID paymentId, String reason, String correlationId) {
		UUID saleId = saleIdForPayment(principal, paymentId);
		ensureSale(principal, saleId);
		PaymentDetail payment = payment(principal, paymentId);
		if ("COMPLETED".equals(payment.status())) {
			throw new BusinessException("PAYMENT_ALREADY_COMPLETED", "No puedes rechazar un pago completado.");
		}
		if ("REJECTED".equals(payment.status())) {
			return saleId;
		}
		OffsetDateTime now = OffsetDateTime.now();
		commandRepository.reject(principal.organizationId(), paymentId, blankToNull(reason), principal.userId(), now);
		auditService.record(principal, "PAYMENT_REJECTED", "PAYMENT", paymentId, "POS", reason, correlationId);
		eventPublisher.publishEvent(new PaymentRejectedV1(
				"payment-rejected:" + paymentId,
				principal.organizationId(),
				principal.branchId(),
				saleId,
				paymentId,
				reason,
				now
		));
		completionService.recalculate(principal.organizationId(), saleId, principal.userId());
		return saleId;
	}

	private UUID saleIdForPayment(GymOsPrincipal principal, UUID paymentId) {
		return salesRepository.saleIdForPayment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
	}

	private PaymentDetail payment(GymOsPrincipal principal, UUID paymentId) {
		return salesRepository.payment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
	}

	private SaleHeader ensureSale(GymOsPrincipal principal, UUID saleId) {
		SaleHeader sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		if (!sale.branchId().equals(principal.branchId())) {
			throw new BusinessException("SALE_BRANCH_MISMATCH", "La venta pertenece a otra sucursal.");
		}
		return sale;
	}

	private String requestHash(ManualPaymentCommand request) {
		return sha256(String.join("|",
				"method=" + normalized(request.method()).toUpperCase(),
				"amount=" + request.amountCents(),
				"reference=" + normalized(request.reference()),
				"receipt=" + normalized(request.receiptObjectKey()),
				"validateNow=" + request.validateNow(),
				"notes=" + normalized(request.notes())
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

	private String receiptObjectKey(UUID organizationId, UUID saleId, UUID paymentId, String originalFilename) {
		String filename = originalFilename == null || originalFilename.isBlank() ? "receipt.bin" : originalFilename;
		String safeFilename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
		return "receipts/" + organizationId + "/" + saleId + "/" + paymentId + "/" + System.currentTimeMillis() + "-" + safeFilename;
	}
}
