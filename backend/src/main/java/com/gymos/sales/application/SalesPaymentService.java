package com.gymos.sales.application;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.application.command.ManualPaymentCommand;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class SalesPaymentService {
	private final SalesPaymentRecordingService recordingService;
	private final SalesPaymentReceiptService receiptService;
	private final SalesPaymentLifecycleService lifecycleService;

	SalesPaymentService(SalesPaymentRecordingService recordingService, SalesPaymentReceiptService receiptService,
			SalesPaymentLifecycleService lifecycleService) {
		this.recordingService = recordingService;
		this.receiptService = receiptService;
		this.lifecycleService = lifecycleService;
	}

	void record(GymOsPrincipal principal, UUID saleId, ManualPaymentCommand request, String correlationId) {
		recordingService.record(principal, saleId, request, correlationId);
	}

	UUID uploadReceipt(GymOsPrincipal principal, UUID paymentId, MultipartFile file, String correlationId) {
		return receiptService.uploadReceipt(principal, paymentId, file, correlationId);
	}

	UUID validate(GymOsPrincipal principal, UUID paymentId, String correlationId) {
		return lifecycleService.validate(principal, paymentId, correlationId);
	}

	UUID reject(GymOsPrincipal principal, UUID paymentId, String reason, String correlationId) {
		return lifecycleService.reject(principal, paymentId, reason, correlationId);
	}
}
