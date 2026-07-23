package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.storage.ObjectStorageService;
import com.gymos.sales.infrastructure.MembershipSaleCommandRepository;
import com.gymos.sales.infrastructure.SaleCommandRepository;
import com.gymos.sales.infrastructure.SalePaymentCompletionRepository;
import com.gymos.sales.infrastructure.SalesPaymentCommandRepository;
import com.gymos.sales.infrastructure.SalesReadRepository;
import com.gymos.sales.infrastructure.SalesRefundCommandRepository;
import com.gymos.sales.infrastructure.SalesReportRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;

final class SalesServiceTestFactory {
	private SalesServiceTestFactory() {
	}

	static SalesService create(JdbcClient jdbcClient, SalesRepository salesRepository, AuditService auditService,
			ObjectStorageService objectStorageService, ApplicationEventPublisher eventPublisher) {
		SalePaymentCompletionService completionService = new SalePaymentCompletionService(
				salesRepository, new SalePaymentCompletionRepository(jdbcClient), eventPublisher);
		SalesLookupService lookupService = new SalesLookupService(salesRepository);
		SalesPaymentCommandRepository paymentCommandRepository = new SalesPaymentCommandRepository(jdbcClient);
		SalesPaymentService paymentService = new SalesPaymentService(
				new SalesPaymentRecordingService(salesRepository, lookupService, paymentCommandRepository, auditService, eventPublisher),
				new SalesPaymentReceiptService(lookupService, paymentCommandRepository, auditService, objectStorageService),
				new SalesPaymentLifecycleService(lookupService, paymentCommandRepository, completionService, auditService, eventPublisher));
		MembershipSalesService membershipSalesService = new MembershipSalesService(
				salesRepository, new MembershipSaleCommandRepository(jdbcClient), completionService,
				auditService, eventPublisher);
		SalesReadRepository salesReadRepository = new SalesReadRepository(jdbcClient);
		SaleDetailQuery saleDetailQuery = new SaleDetailQuery(salesRepository, salesReadRepository);
		SaleCreationService saleCreationService = new SaleCreationService(salesRepository, new SaleLineResolver(salesRepository),
				new SaleCommandRepository(jdbcClient), auditService, eventPublisher);
		SalesRefundCommandRepository refundCommandRepository = new SalesRefundCommandRepository(jdbcClient);
		SalesRefundService refundService = new SalesRefundService(
				lookupService,
				new SalesRefundIdempotencyService(salesRepository),
				new SalesRefundValidationService(salesRepository, lookupService),
				new SalesRefundProcessor(refundCommandRepository, auditService)
		);
		SalesPointOfSaleService pointOfSaleService = new SalesPointOfSaleService(
				saleDetailQuery, saleCreationService, paymentService, refundService, completionService);
		return new SalesService(
				saleDetailQuery,
				new SalesReportingQuery(salesReadRepository, new SalesReportRepository(jdbcClient)),
				pointOfSaleService,
				membershipSalesService
		);
	}
}
