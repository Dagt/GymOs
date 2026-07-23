package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.storage.ObjectStorageService;
import com.gymos.sales.infrastructure.SalesPaymentCommandRepository;
import com.gymos.sales.model.SaleHeader;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class SalesPaymentReceiptService {
	private final SalesLookupService lookupService;
	private final SalesPaymentCommandRepository commandRepository;
	private final AuditService auditService;
	private final ObjectStorageService objectStorageService;

	SalesPaymentReceiptService(SalesLookupService lookupService, SalesPaymentCommandRepository commandRepository,
			AuditService auditService, ObjectStorageService objectStorageService) {
		this.lookupService = lookupService;
		this.commandRepository = commandRepository;
		this.auditService = auditService;
		this.objectStorageService = objectStorageService;
	}

	UUID uploadReceipt(GymOsPrincipal principal, UUID paymentId, MultipartFile file, String correlationId) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException("PAYMENT_RECEIPT_REQUIRED", "Selecciona un archivo de comprobante.");
		}
		UUID saleId = lookupService.saleIdForPayment(principal, paymentId);
		SaleHeader sale = lookupService.saleForBranch(principal, saleId);
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

	private String receiptObjectKey(UUID organizationId, UUID saleId, UUID paymentId, String originalFilename) {
		String filename = originalFilename == null || originalFilename.isBlank() ? "receipt.bin" : originalFilename;
		return "receipts/" + organizationId + "/" + saleId + "/" + paymentId + "/" + System.currentTimeMillis() + "-" + filename.replaceAll("[^A-Za-z0-9._-]", "_");
	}
}
