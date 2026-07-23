package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.sales.application.result.SaleDetailResult;
import com.gymos.sales.infrastructure.SalesReadRepository;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.model.SaleHeader;
import com.gymos.sales.model.SaleSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SaleDetailQuery {
	private final SalesRepository salesRepository;
	private final SalesReadRepository salesReadRepository;

	SaleDetailQuery(SalesRepository salesRepository, SalesReadRepository salesReadRepository) {
		this.salesRepository = salesRepository;
		this.salesReadRepository = salesReadRepository;
	}

	List<SaleSummary> recent(UUID organizationId, UUID branchId) {
		return salesReadRepository.recentSales(organizationId, branchId);
	}

	SaleDetailResult detail(UUID organizationId, UUID saleId) {
		SaleHeader sale = salesRepository.saleHeader(organizationId, saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		return new SaleDetailResult(
				sale,
				salesReadRepository.saleItems(organizationId, saleId),
				salesReadRepository.payments(organizationId, saleId),
				salesReadRepository.refunds(organizationId, saleId),
				salesReadRepository.membershipsForSale(organizationId, saleId)
		);
	}
}
