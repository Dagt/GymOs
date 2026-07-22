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
		SalesPaymentService paymentService = new SalesPaymentService(
				salesRepository, new SalesPaymentCommandRepository(jdbcClient), completionService,
				auditService, objectStorageService, eventPublisher);
		MembershipSalesService membershipSalesService = new MembershipSalesService(
				salesRepository, new MembershipSaleCommandRepository(jdbcClient), completionService,
				auditService, eventPublisher);
		return new SalesService(
				salesRepository,
				new SalesReadRepository(jdbcClient),
				new SalesReportRepository(jdbcClient),
				new SaleCreationService(salesRepository, new SaleLineResolver(salesRepository),
						new SaleCommandRepository(jdbcClient), auditService, eventPublisher),
				paymentService,
				new SalesRefundService(salesRepository, new SalesRefundCommandRepository(jdbcClient), auditService),
				membershipSalesService,
				completionService
		);
	}
}
